package com.cotato.nextstation.domain.place.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * "장소 데이터" 구글 시트를 읽어 카카오 로컬 "키워드로 장소 검색" API로
 * 주소/좌표/카카오맵 URL을 보강하고, 결과를 Sheets API로 시트에 직접 되써넣는 1회성 배치 스크립트.
 * 확정된 행은 주소~카카오맵 URL 컬럼에, 미확정 행은 배치 매칭 메모 컬럼에 사유/후보를 기록한다.
 * 검수 메모는 사람이 쓰는 컬럼이라 배치가 건드리지 않는다.
 *
 * <p>되써넣을 위치는 헤더 이름으로 찾는다. 열 문자를 고정하면 시트 앞에 컬럼이 추가될 때 대상이 아닌 컬럼을 덮어쓴다.
 */
public final class PlaceGeocodingBatch {

    private static final Logger log = LoggerFactory.getLogger(PlaceGeocodingBatch.class);

    private static final String KAKAO_KEYWORD_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String KAKAO_ADDRESS_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/address.json";
    private static final Path ENRICHED_OUTPUT = Path.of("place-geocoding-output/place_geocoded.csv");
    private static final Path MANUAL_REVIEW_OUTPUT = Path.of("place-geocoding-output/place_manual_review.csv");
    private static final Path SERVICE_ACCOUNT_KEY_PATH = Path.of("credentials/google-sheets-service-account.json");
    private static final long REQUEST_INTERVAL_MILLIS = 150;
    private static final int MAX_RETRY = 3;

    // 주소 검색 좌표(행정구역 중심)와 실제 카카오맵 POI 좌표(공원/유적 등은 입구·중심에 찍힘) 사이 거리가 50m를 넘는 경우가 많아(실측 77~255m) 1000m로 넉넉히 잡음.
    private static final int NEARBY_SEARCH_RADIUS_METERS = 1000;

    private static final String PROGRESS_STATUS_DONE = "검수 완료";
    private static final int HEADER_ROW_NUMBER = 1;

    private static final String[] OUTPUT_HEADERS = {
            "담당자", "진행 상태", "호선", "역명", "카테고리", "장소명", "해시태그 1", "해시태그 2", "한 줄 설명", "검수 메모",
            "주소", "전화번호", "x좌표", "y좌표", "카카오맵 URL"
    };

    // 확정된 행에 한 범위로 묶어 쓰는 컬럼. 시트에서 이 순서대로 연속이어야 한다.
    private static final List<String> ADDRESS_BLOCK_HEADERS =
            List.of("주소", "전화번호", "x좌표", "y좌표", "카카오맵 URL");
    private static final String MATCH_NOTE_HEADER = "배치 매칭 메모 (BE)";

    private PlaceGeocodingBatch() {
    }

    public static void main(String[] args) throws IOException, InterruptedException, GeneralSecurityException {
        String kakaoApiKey = requireEnv("KAKAO_REST_API_KEY");
        String sheetCsvUrl = requireEnv("PLACE_SHEET_CSV_URL");
        String spreadsheetId = requireEnv("PLACE_SPREADSHEET_ID");

        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        ObjectMapper objectMapper = new ObjectMapper();

        verifyKakaoApiKey(httpClient, kakaoApiKey);

        Sheets sheetsService = buildSheetsService();
        String sheetTitle = resolveSheetTitle(sheetsService, spreadsheetId, extractGid(sheetCsvUrl));

        SheetSnapshot snapshot = downloadIncludedRows(httpClient, sheetCsvUrl);
        SheetLayout layout = resolveLayout(snapshot.headers());
        List<SheetRow> targetRows = snapshot.rows();
        log.info("진행 상태='{}' 대상 행 {}건 로드", PROGRESS_STATUS_DONE, targetRows.size());

        List<Map<String, String>> enrichedRows = new ArrayList<>();
        List<Map<String, String>> manualReviewRows = new ArrayList<>();
        List<ValueRange> sheetUpdates = new ArrayList<>();

        int processed = 0;
        int fallbackResolved = 0;
        int addressResolved = 0;
        int nearbyUrlResolved = 0;
        for (SheetRow sheetRow : targetRows) {
            CSVRecord row = sheetRow.record();
            String stationName = row.get("역명").trim();
            String placeName = row.get("장소명").trim();

            Resolution resolution = resolveWithQuery(httpClient, objectMapper, kakaoApiKey,
                    stationName + " " + placeName, placeName);

            if (!resolution.confirmed() && !"AMBIGUOUS".equals(resolution.reasonPrefix())) {
                Thread.sleep(REQUEST_INTERVAL_MILLIS);
                Resolution fallback = resolveWithQuery(httpClient, objectMapper, kakaoApiKey, placeName, placeName);
                if (fallback.confirmed() || fallback.candidates().size() > resolution.candidates().size()) {
                    if (fallback.confirmed()) {
                        fallbackResolved++;
                        log.info("[재검증 필요] 역명 없이 확정됨: {} {} -> {}({})",
                                stationName, placeName,
                                fallback.match().path("place_name").asText(""),
                                fallback.match().path("address_name").asText(""));
                    }
                    resolution = fallback;
                }
            }

            String manualAddress = row.get("주소").trim();
            if (!resolution.confirmed() && !manualAddress.isBlank()) {
                Thread.sleep(REQUEST_INTERVAL_MILLIS);
                JsonNode addressMatch = resolveByAddress(httpClient, objectMapper, kakaoApiKey, manualAddress);
                if (addressMatch != null) {
                    addressResolved++;
                    log.info("주소 검색으로 확정됨: {} {} ({}) -> {}",
                            stationName, placeName, manualAddress, addressMatch.path("address_name").asText(""));

                    Thread.sleep(REQUEST_INTERVAL_MILLIS);
                    JsonNode nearbyPlace = findNearbyPlaceUrl(httpClient, objectMapper, kakaoApiKey,
                            placeName, addressMatch.path("x").asText(""), addressMatch.path("y").asText(""));
                    if (nearbyPlace != null) {
                        nearbyUrlResolved++;
                        log.info("카카오맵 URL 추가 보강됨: {} {} -> {}",
                                stationName, placeName, nearbyPlace.path("place_url").asText(""));
                    }
                    resolution = new Resolution(true, mergeAddressAndNearby(addressMatch, nearbyPlace), null, List.of());
                }
            }

            if (resolution.confirmed()) {
                enrichedRows.add(toEnrichedRow(row, resolution.match()));
                sheetUpdates.add(toAddressValueRange(layout, sheetTitle, sheetRow.rowNumber(), row, resolution.match()));
                sheetUpdates.add(toClearReviewNoteValueRange(layout, sheetTitle, sheetRow.rowNumber()));
            } else {
                manualReviewRows.add(toManualReviewRow(row, resolution.reason(), resolution.candidates()));
                sheetUpdates.add(toReviewNoteValueRange(layout, sheetTitle, sheetRow.rowNumber(),
                        resolution.reason(), resolution.candidates()));
            }

            processed++;
            if (processed % 50 == 0) {
                log.info("진행 {}/{}", processed, targetRows.size());
            }

            Thread.sleep(REQUEST_INTERVAL_MILLIS);
        }
        log.info("역명 없이 재검색해서 추가로 확정된 건수: {}", fallbackResolved);
        log.info("수동 입력 주소로 확정된 건수: {}", addressResolved);
        log.info("주소 확정 건 중 카카오맵 URL까지 보강된 건수: {}", nearbyUrlResolved);

        writeCsv(ENRICHED_OUTPUT, OUTPUT_HEADERS, enrichedRows);
        writeCsv(MANUAL_REVIEW_OUTPUT,
                new String[]{"역명", "장소명", "카테고리", "사유", "후보"},
                manualReviewRows);
        writeBackToSheet(sheetsService, spreadsheetId, sheetUpdates);

        log.info("완료: 확정 {}건 -> {}, 수동 확인 {}건 -> {}",
                enrichedRows.size(), ENRICHED_OUTPUT, manualReviewRows.size(), MANUAL_REVIEW_OUTPUT);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("환경변수 " + name + "가 설정되어 있지 않습니다. .env.local 값을 실행 환경에 로드했는지 확인하세요.");
        }
        return value;
    }

    private static void verifyKakaoApiKey(HttpClient httpClient, String kakaoApiKey)
            throws IOException, InterruptedException {
        URI uri = URI.create(KAKAO_KEYWORD_SEARCH_URL + "?query=" + URLEncoder.encode("서울역", StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "KakaoAK " + kakaoApiKey)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("카카오 API 연결 확인 실패", e);
        }

        if (response.statusCode() == 401) {
            throw new IllegalStateException(
                    "카카오 API 키가 유효하지 않습니다 (401 Unauthorized). KAKAO_REST_API_KEY 값을 확인하세요.");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("카카오 API 사전 점검 실패: HTTP " + response.statusCode() + " - " + response.body());
        }

        log.info("카카오 API 키 확인 완료");
    }

    private static Sheets buildSheetsService() throws IOException, GeneralSecurityException {
        GoogleCredentials credentials;
        try (InputStream keyStream = Files.newInputStream(SERVICE_ACCOUNT_KEY_PATH)) {
            credentials = GoogleCredentials.fromStream(keyStream).createScoped(List.of(SheetsScopes.SPREADSHEETS));
        }
        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("nextstation-place-geocoding-batch")
                .build();
    }

    private static String extractGid(String sheetCsvUrl) {
        Matcher matcher = Pattern.compile("gid=(\\d+)").matcher(sheetCsvUrl);
        if (!matcher.find()) {
            throw new IllegalStateException("PLACE_SHEET_CSV_URL에서 gid를 찾을 수 없습니다: " + sheetCsvUrl);
        }
        return matcher.group(1);
    }

    private static String resolveSheetTitle(Sheets sheetsService, String spreadsheetId, String gid) throws IOException {
        long gidValue = Long.parseLong(gid);
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        for (com.google.api.services.sheets.v4.model.Sheet sheet : spreadsheet.getSheets()) {
            SheetProperties properties = sheet.getProperties();
            if (properties.getSheetId() != null && properties.getSheetId() == gidValue) {
                return properties.getTitle();
            }
        }
        throw new IllegalStateException("gid=" + gid + "에 해당하는 시트를 spreadsheetId=" + spreadsheetId + "에서 찾을 수 없습니다.");
    }

    private record SheetRow(int rowNumber, CSVRecord record) {
    }

    private record SheetSnapshot(List<String> headers, List<SheetRow> rows) {
    }

    /** 되써넣을 컬럼의 0-based 헤더 인덱스 */
    record SheetLayout(int addressBlockIndex, int matchNoteIndex) {

        String addressBlockRange(String sheetTitle, int rowNumber) {
            String start = columnLetter(addressBlockIndex);
            String end = columnLetter(addressBlockIndex + ADDRESS_BLOCK_HEADERS.size() - 1);
            return sheetTitle + "!" + start + rowNumber + ":" + end + rowNumber;
        }

        String matchNoteRange(String sheetTitle, int rowNumber) {
            return sheetTitle + "!" + columnLetter(matchNoteIndex) + rowNumber;
        }
    }

    static SheetLayout resolveLayout(List<String> headers) {
        int addressBlockIndex = headers.indexOf(ADDRESS_BLOCK_HEADERS.get(0));
        if (addressBlockIndex < 0) {
            throw new IllegalStateException("시트에서 '주소' 컬럼을 찾을 수 없습니다. headers=" + headers);
        }

        // 한 범위로 묶어 쓰므로 중간에 다른 컬럼이 끼면 그 칸까지 덮어쓴다.
        List<String> actual = headers.subList(addressBlockIndex,
                Math.min(addressBlockIndex + ADDRESS_BLOCK_HEADERS.size(), headers.size()));
        if (!ADDRESS_BLOCK_HEADERS.equals(actual)) {
            throw new IllegalStateException("주소~카카오맵 URL 컬럼이 연속이어야 합니다. expected="
                    + ADDRESS_BLOCK_HEADERS + ", actual=" + actual);
        }

        int matchNoteIndex = headers.indexOf(MATCH_NOTE_HEADER);
        if (matchNoteIndex < 0) {
            throw new IllegalStateException("시트에서 '" + MATCH_NOTE_HEADER + "' 컬럼을 찾을 수 없습니다. headers=" + headers);
        }

        log.info("시트 컬럼 위치 확인: 주소={}, {}={}",
                columnLetter(addressBlockIndex), MATCH_NOTE_HEADER, columnLetter(matchNoteIndex));
        return new SheetLayout(addressBlockIndex, matchNoteIndex);
    }

    /** 0-based 헤더 인덱스를 A1 표기 열 문자로 바꾼다. 0 -> A, 25 -> Z, 26 -> AA. */
    static String columnLetter(int index) {
        StringBuilder letters = new StringBuilder();
        for (int i = index; i >= 0; i = i / 26 - 1) {
            letters.insert(0, (char) ('A' + i % 26));
        }
        return letters.toString();
    }

    /**
     * 시트 컬럼이 앞에 추가돼도 안 깨지도록 첫 줄을 헤더로 읽는다.
     * 시트 끝에 이름 없는 빈 컬럼이 있어 중복·누락 헤더를 허용해야 한다.
     */
    private static SheetSnapshot downloadIncludedRows(HttpClient httpClient, String sheetCsvUrl)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(sheetCsvUrl)).GET().build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("구글 시트 CSV 다운로드 실패: " + sheetCsvUrl, e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("구글 시트 CSV 다운로드 실패: HTTP " + response.statusCode());
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL)
                .setAllowMissingColumnNames(true)
                .build();

        try (CSVParser parser = CSVParser.parse(new StringReader(response.body()), format)) {
            List<SheetRow> result = new ArrayList<>();
            int skippedResolved = 0;
            int rowNumber = HEADER_ROW_NUMBER;
            for (CSVRecord record : parser) {
                rowNumber++;
                if (!PROGRESS_STATUS_DONE.equals(record.get("진행 상태").trim())) {
                    continue;
                }
                if (record.get("장소명").isBlank()) {
                    continue;
                }
                // 재검색하면 place id가 바뀔 수 있어 운영 반영의 (역명, place id) 매칭이 끊긴다.
                // 장소를 교체했다면 그 행의 카카오맵 URL을 비우고 돌린다.
                if (!record.get("카카오맵 URL").isBlank()) {
                    skippedResolved++;
                    continue;
                }
                result.add(new SheetRow(rowNumber, record));
            }
            log.info("이미 카카오맵 URL이 있어 건너뛴 행: {}건", skippedResolved);
            return new SheetSnapshot(parser.getHeaderNames(), result);
        }
    }

    private static JsonNode searchKakaoKeyword(HttpClient httpClient, ObjectMapper objectMapper,
                                                String kakaoApiKey, String query) throws InterruptedException {
        return searchKakao(httpClient, objectMapper, kakaoApiKey,
                buildSearchUri(KAKAO_KEYWORD_SEARCH_URL, query, null, null), query);
    }

    private static JsonNode searchKakaoAddress(HttpClient httpClient, ObjectMapper objectMapper,
                                                String kakaoApiKey, String query) throws InterruptedException {
        return searchKakao(httpClient, objectMapper, kakaoApiKey,
                buildSearchUri(KAKAO_ADDRESS_SEARCH_URL, query, null, null), query);
    }

    // 주소 검색으로 얻은 좌표 반경 내에서 장소명을 다시 찾아 place_url(카카오맵 URL)만 보강하기 위한 검색
    private static JsonNode searchKakaoNearbyKeyword(HttpClient httpClient, ObjectMapper objectMapper,
                                                       String kakaoApiKey, String query, String x, String y)
            throws InterruptedException {
        return searchKakao(httpClient, objectMapper, kakaoApiKey,
                buildSearchUri(KAKAO_KEYWORD_SEARCH_URL, query, x, y), query);
    }

    private static URI buildSearchUri(String baseUrl, String query, String x, String y) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String uriString = baseUrl + "?query=" + encodedQuery + "&size=15&page=1";
        if (x != null && y != null) {
            uriString += "&x=" + x + "&y=" + y + "&radius=" + NEARBY_SEARCH_RADIUS_METERS;
        }
        return URI.create(uriString);
    }

    private static JsonNode searchKakao(HttpClient httpClient, ObjectMapper objectMapper,
                                         String kakaoApiKey, URI uri, String query) throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "KakaoAK " + kakaoApiKey)
                .GET()
                .build();

        long backoffMillis = 300;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200) {
                    return objectMapper.readTree(response.body()).path("documents");
                }
                if (response.statusCode() == 429) {
                    log.warn("카카오 API 요청 제한(429), {}ms 후 재시도 (query={})", backoffMillis, query);
                    Thread.sleep(backoffMillis);
                    backoffMillis *= 2;
                    continue;
                }
                if (response.statusCode() == 401) {
                    throw new IllegalStateException("카카오 API 키가 처리 도중 무효화되었습니다 (401). 중단합니다.");
                }
                log.warn("카카오 API 오류 응답: status={}, query={}", response.statusCode(), query);
                return null;
            } catch (IOException e) {
                log.warn("카카오 API 호출 실패(query={}, attempt={}): {}", query, attempt, e.getMessage());
                Thread.sleep(backoffMillis);
                backoffMillis *= 2;
            }
        }
        return null;
    }

    private record Resolution(boolean confirmed, JsonNode match, String reason, List<String> candidates) {
        String reasonPrefix() {
            int idx = reason.indexOf('(');
            return idx >= 0 ? reason.substring(0, idx) : reason;
        }
    }

    private static Resolution resolveWithQuery(HttpClient httpClient, ObjectMapper objectMapper,
                                                String kakaoApiKey, String query, String placeName) throws InterruptedException {
        JsonNode documents = searchKakaoKeyword(httpClient, objectMapper, kakaoApiKey, query);
        if (documents == null) {
            return new Resolution(false, null, "API_ERROR", List.of());
        }
        if (documents.isEmpty()) {
            return new Resolution(false, null, "NO_RESULT", List.of());
        }
        List<JsonNode> exactMatches = findExactNameMatches(documents, placeName);
        if (exactMatches.size() == 1) {
            return new Resolution(true, exactMatches.get(0), null, List.of());
        }
        if (exactMatches.isEmpty()) {
            return new Resolution(false, null, "NO_EXACT_MATCH", toCandidateList(documents));
        }
        return new Resolution(false, null, "AMBIGUOUS(" + exactMatches.size() + ")", toCandidateList(exactMatches));
    }

    // 주소 검색 결과(place_url 없음) 반경 내에서 장소명으로 실제 업체를 찾아 카카오맵 URL을 보강한다.
    // 못 찾으면(하천/공원 등 실제 업체가 아닌 경우) null 반환 — 이때는 URL 없이 둔다.
    private static JsonNode findNearbyPlaceUrl(HttpClient httpClient, ObjectMapper objectMapper, String kakaoApiKey,
                                                String placeName, String x, String y) throws InterruptedException {
        if (x.isBlank() || y.isBlank()) {
            return null;
        }
        JsonNode documents = searchKakaoNearbyKeyword(httpClient, objectMapper, kakaoApiKey, placeName, x, y);
        if (documents == null || documents.isEmpty()) {
            return null;
        }
        // 카카오 키워드 검색은 기본이 관련도순 정렬이라(거리순 아님), 같은 이름이 여러 구간에 걸쳐
        // 있는 경우(하천 등) 이름이 정확히 일치하는 것 중 실제로 가장 가까운 것을 직접 골라야 한다.
        List<JsonNode> exactMatches = findExactNameMatches(documents, placeName);
        return exactMatches.stream()
                .min(Comparator.comparingInt(doc -> doc.path("distance").asInt(Integer.MAX_VALUE)))
                .orElse(null);
    }

    // 주소/좌표는 수동 입력 주소 검색 결과(정확)를, 전화번호/카카오맵 URL은 반경 검색으로 찾은 실제 업체(있으면)를 합친다.
    private static JsonNode mergeAddressAndNearby(JsonNode addressMatch, JsonNode nearbyPlace) {
        ObjectNode merged = addressMatch.deepCopy();
        if (nearbyPlace != null) {
            merged.put("phone", nearbyPlace.path("phone").asText(""));
            merged.put("place_url", nearbyPlace.path("place_url").asText(""));
        }
        return merged;
    }

    private static JsonNode resolveByAddress(HttpClient httpClient, ObjectMapper objectMapper,
                                              String kakaoApiKey, String address) throws InterruptedException {
        JsonNode documents = searchKakaoAddress(httpClient, objectMapper, kakaoApiKey, address);
        if (documents == null || documents.isEmpty()) {
            return null;
        }
        return documents.get(0);
    }

    private static List<JsonNode> findExactNameMatches(JsonNode documents, String placeName) {
        String normalizedTarget = normalizePlaceName(placeName);
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode document : documents) {
            if (normalizePlaceName(document.path("place_name").asText("")).equals(normalizedTarget)) {
                matches.add(document);
            }
        }
        return matches;
    }

    private static String normalizePlaceName(String name) {
        return name.trim().replaceAll("\\s+", "");
    }

    private static String resolvePhone(CSVRecord row, JsonNode document) {
        return row.get("전화번호").isBlank() ? document.path("phone").asText("") : row.get("전화번호");
    }

    private static Map<String, String> toEnrichedRow(CSVRecord row, JsonNode document) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("담당자", row.get("담당자"));
        result.put("진행 상태", row.get("진행 상태"));
        result.put("호선", row.get("호선"));
        result.put("역명", row.get("역명"));
        result.put("카테고리", row.get("카테고리"));
        result.put("장소명", row.get("장소명"));
        result.put("해시태그 1", row.get("해시태그 1"));
        result.put("해시태그 2", row.get("해시태그 2"));
        result.put("한 줄 설명", row.get("한 줄 설명"));
        result.put("검수 메모", row.get("검수 메모"));
        result.put("주소", document.path("address_name").asText(""));
        result.put("전화번호", resolvePhone(row, document));
        result.put("x좌표", document.path("x").asText(""));
        result.put("y좌표", document.path("y").asText(""));
        result.put("카카오맵 URL", document.path("place_url").asText(""));
        return result;
    }

    private static Map<String, String> toManualReviewRow(CSVRecord row, String reason, List<String> candidates) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("역명", row.get("역명"));
        result.put("장소명", row.get("장소명"));
        result.put("카테고리", row.get("카테고리"));
        result.put("사유", reason);
        result.put("후보", String.join(" | ", candidates));
        return result;
    }

    private static List<String> toCandidateList(Iterable<JsonNode> documents) {
        return StreamSupport.stream(documents.spliterator(), false)
                .limit(5)
                .map(doc -> doc.path("place_name").asText("") + "(" + doc.path("address_name").asText("") + ")")
                .collect(Collectors.toList());
    }

    private static ValueRange toAddressValueRange(SheetLayout layout, String sheetTitle, int rowNumber,
                                                  CSVRecord row, JsonNode document) {
        // ADDRESS_BLOCK_HEADERS 순서와 맞춰야 한다. resolveLayout이 그 순서를 시트에서 검증한다.
        List<Object> values = List.of(
                document.path("address_name").asText(""),
                resolvePhone(row, document),
                document.path("x").asText(""),
                document.path("y").asText(""),
                document.path("place_url").asText("")
        );
        return new ValueRange()
                .setRange(layout.addressBlockRange(sheetTitle, rowNumber))
                .setValues(List.of(values));
    }

    private static ValueRange toReviewNoteValueRange(SheetLayout layout, String sheetTitle, int rowNumber,
                                                     String reason, List<String> candidates) {
        // "배치 매칭 메모 (BE)" 전용 — 사람이 쓰는 검수 메모와 분리해서 덮어쓰지 않는다.
        String note = candidates.isEmpty() ? reason : reason + ": " + String.join(" | ", candidates);
        return new ValueRange()
                .setRange(layout.matchNoteRange(sheetTitle, rowNumber))
                .setValues(List.of(List.of(note)));
    }

    private static ValueRange toClearReviewNoteValueRange(SheetLayout layout, String sheetTitle, int rowNumber) {
        // 확정된 행은 예전 미확정 사유가 남아있으면 헷갈릴 수 있어 비운다.
        return new ValueRange()
                .setRange(layout.matchNoteRange(sheetTitle, rowNumber))
                .setValues(List.of(List.of("")));
    }

    private static void writeBackToSheet(Sheets sheetsService, String spreadsheetId, List<ValueRange> updates)
            throws IOException {
        if (updates.isEmpty()) {
            return;
        }
        BatchUpdateValuesRequest body = new BatchUpdateValuesRequest()
                .setValueInputOption("USER_ENTERED")
                .setData(updates);
        sheetsService.spreadsheets().values().batchUpdate(spreadsheetId, body).execute();
        log.info("시트에 {}건 반영 완료", updates.size());
    }

    private static void writeCsv(Path path, String[] headers, List<Map<String, String>> rows) throws IOException {
        Files.createDirectories(path.getParent());
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers).build();
        try (CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(path, StandardCharsets.UTF_8), format)) {
            for (Map<String, String> row : rows) {
                List<String> values = new ArrayList<>();
                for (String header : headers) {
                    values.add(row.getOrDefault(header, ""));
                }
                printer.printRecord(values);
            }
        }
    }
}
