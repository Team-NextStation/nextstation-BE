package com.cotato.nextstation.domain.place.init;

import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * 정제된 CSV를 읽어 장소 데이터를 시딩하는 러너.
 *
 * <p>{@code resources/data/places.csv}에서 Place와 PlaceTagMapping을 적재한다. 사진은
 * {@code PlaceImageUploadBatch}가 생성한 {@code resources/data/place-images.csv}를 카카오 place id로
 * 조인해 PlaceImage로 저장한다.
 *
 * <p>두 CSV의 해시를 {@link #SEED_HASH_MARKER}에 기록해두고 이전 해시와 다를 때만 기존 데이터를 지우고
 * 다시 적재한다. 이미지 CSV만 변경된 경우도 감지해야 하므로 해시는 두 파일을 함께 계산한다.
 * 변경이 없으면 건너뛰므로 재기동할 때마다 로컬 테스트 데이터가 사라지지 않는다.
 *
 * <p>Station은 역명, Category와 PlaceTag는 코드와 이름으로 참조한다. 따라서 StationDataSeeder와
 * 마스터 데이터를 넣는 data.sql이 먼저 실행되어야 한다.
 */
@Slf4j
@Component
@Profile("!prod")
@Order(3)
@RequiredArgsConstructor
public class PlaceSeeder implements ApplicationRunner {

    private static final String SEED_CSV_PATH = "data/places.csv";
    private static final String SEED_IMAGE_CSV_PATH = "data/place-images.csv";
    private static final String PROGRESS_STATUS_DONE = "검수 완료";
    // CSV 해시를 기록하는 마커 파일. build/는 이미 gitignore 대상이라 별도 설정이 필요하지 않다.
    private static final Path SEED_HASH_MARKER = Path.of("build", "place-seed.sha256");

    private final PlaceRepository placeRepository;
    private final PlaceSeedWriter placeSeedWriter;

    @Override
    public void run(ApplicationArguments args) throws IOException {

        byte[] csvBytes;
        try (InputStream csvStream = new ClassPathResource(SEED_CSV_PATH).getInputStream()) {
            csvBytes = csvStream.readAllBytes();
        }
        byte[] imageCsvBytes = readImageCsvBytes();

        String currentHash = sha256(csvBytes, imageCsvBytes);
        boolean csvChanged = !currentHash.equals(readStoredHash());

        if (placeRepository.count() > 0 && !csvChanged) {
            log.info("places.csv/place-images.csv 변경 없음, place 시딩을 건너뜁니다. count={}", placeRepository.count());
            return;
        }

        log.info("place 시딩 시작(csvChanged={}): source={}, imageSource={}",
                csvChanged, SEED_CSV_PATH, SEED_IMAGE_CSV_PATH);

        Map<String, List<PlaceSeedImage>> imagesByKakaoPlaceId = readImages(imageCsvBytes);
        List<PlaceSeedRow> rows = readSeedRows(csvBytes, imagesByKakaoPlaceId);
        placeSeedWriter.write(rows);
        writeStoredHash(currentHash);
    }

    private String readStoredHash() throws IOException {
        if (!Files.exists(SEED_HASH_MARKER)) {
            return "";
        }
        return Files.readString(SEED_HASH_MARKER, StandardCharsets.UTF_8).trim();
    }

    private void writeStoredHash(String hash) throws IOException {
        Files.createDirectories(SEED_HASH_MARKER.getParent());
        Files.writeString(SEED_HASH_MARKER, hash, StandardCharsets.UTF_8);
    }

    private String sha256(byte[]... contents) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] content : contents) {
                digest.update(content);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }

    /** 이미지 목록 CSV는 사진 수집 전에는 존재하지 않는다. 파일이 없으면 이미지 없이 시딩한다. */
    private byte[] readImageCsvBytes() throws IOException {
        ClassPathResource resource = new ClassPathResource(SEED_IMAGE_CSV_PATH);
        if (!resource.exists()) {
            log.info("{} 없음, 장소 이미지 없이 시딩합니다.", SEED_IMAGE_CSV_PATH);
            return new byte[0];
        }
        try (InputStream stream = resource.getInputStream()) {
            return stream.readAllBytes();
        }
    }

    /**
     * 카카오 place id별로 노출 순서대로 정렬된 이미지 목록을 반환한다.
     * 헤더 위치가 바뀌어도 영향을 받지 않도록 이름 기반으로 조회한다.
     */
    Map<String, List<PlaceSeedImage>> readImages(byte[] imageCsvBytes) throws IOException {
        if (imageCsvBytes.length == 0) {
            return Map.of();
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .build();

        Map<String, SortedMap<Integer, PlaceSeedImage>> imagesByPlaceId = new HashMap<>();
        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(new ByteArrayInputStream(imageCsvBytes), StandardCharsets.UTF_8),
                format)) {
            for (CSVRecord record : parser) {
                String kakaoPlaceId = record.get("카카오 place id").trim();
                String imageUrl = record.get("이미지 URL").trim();
                if (kakaoPlaceId.isBlank() || imageUrl.isBlank()) {
                    log.warn("카카오 place id 또는 이미지 URL이 비어 있어 제외합니다. row={}", record.getRecordNumber());
                    continue;
                }

                Integer sortOrder = parseSortOrder(record, kakaoPlaceId);
                if (sortOrder == null) {
                    continue;
                }

                // 출처 컬럼은 뒤에 추가되어 이전 버전 파일에는 없다. 컬럼이 없으면 빈 값으로 처리한다.
                String source = record.isMapped("출처") ? record.get("출처").trim() : "";
                PlaceSeedImage replaced = imagesByPlaceId
                        .computeIfAbsent(kakaoPlaceId, id -> new TreeMap<>())
                        .put(sortOrder, new PlaceSeedImage(imageUrl, source.isBlank() ? null : source));
                if (replaced != null) {
                    log.warn("같은 순서의 이미지가 중복되어 앞의 것을 덮어씁니다. kakaoPlaceId={}, 순서={}, 이전={}",
                            kakaoPlaceId, sortOrder, replaced.imageUrl());
                }
            }
        }

        Map<String, List<PlaceSeedImage>> result = new HashMap<>();
        imagesByPlaceId.forEach((placeId, images) -> result.put(placeId, List.copyOf(images.values())));
        log.info("장소 이미지 목록 로드 완료: placeCount={}", result.size());
        return result;
    }

    /** 순서 컬럼이 숫자가 아니면 그 행만 제외한다. 시딩 전체를 세우지 않는다. */
    private Integer parseSortOrder(CSVRecord record, String kakaoPlaceId) {
        String value = record.get("순서").trim();
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            log.warn("순서가 숫자가 아니라 제외합니다. kakaoPlaceId={}, 순서={}, row={}",
                    kakaoPlaceId, value, record.getRecordNumber());
            return null;
        }
    }

    /**
     * 카카오맵 URL은 {@code https://place.map.kakao.com/{id}} 형식이므로 마지막 경로 조각이 place id다.
     * 끝의 슬래시와 쿼리·프래그먼트를 떼어내지 않으면 조인 키가 어긋나 사진이 조용히 붙지 않는다.
     */
    String extractKakaoPlaceId(String kakaoPlaceUrl) {
        if (kakaoPlaceUrl == null || kakaoPlaceUrl.isBlank()) {
            return null;
        }
        String url = kakaoPlaceUrl.trim().split("[?#]")[0].replaceAll("/+$", "");
        String id = url.substring(url.lastIndexOf('/') + 1);
        return id.isBlank() ? null : id;
    }

    /**
     * 시트 컬럼이 앞에 추가돼도 안 깨지도록 첫 줄을 헤더로 읽는다.
     * places.csv 끝에 이름 없는 빈 컬럼이 있어 중복·누락 헤더를 허용해야 한다.
     */
    List<PlaceSeedRow> readSeedRows(byte[] csvBytes,
                                    Map<String, List<PlaceSeedImage>> imagesByKakaoPlaceId) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL)
                .setAllowMissingColumnNames(true)
                .build();

        List<PlaceSeedRow> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(new ByteArrayInputStream(csvBytes), StandardCharsets.UTF_8),
                format)) {
            for (CSVRecord record : parser) {
                if (!PROGRESS_STATUS_DONE.equals(record.get("진행 상태").trim())) {
                    continue;
                }
                String placeName = record.get("장소명").trim();
                if (placeName.isBlank()) {
                    continue;
                }

                String xCoordText = record.get("x좌표").trim();
                String yCoordText = record.get("y좌표").trim();
                if (xCoordText.isBlank() || yCoordText.isBlank()) {
                    log.warn("좌표가 비어 있어 시딩에서 제외합니다. row={}, 장소명={}", record.getRecordNumber(), placeName);
                    continue;
                }

                List<String> hashtagTexts = Stream.of(record.get("해시태그 1"), record.get("해시태그 2"))
                        .map(String::trim)
                        .filter(tag -> !tag.isBlank())
                        .toList();

                String kakaoPlaceUrl = blankToNull(record.get("카카오맵 URL"));
                String kakaoPlaceId = extractKakaoPlaceId(kakaoPlaceUrl);
                // 운영 반영이 (역명, place id)로 기존 행을 찾는다. id가 없으면 매번 신규로 적재된다.
                if (kakaoPlaceId == null) {
                    log.warn("카카오맵 URL이 없어 시딩에서 제외합니다. row={}, 역명={}, 장소명={}",
                            record.getRecordNumber(), record.get("역명").trim(), placeName);
                    continue;
                }
                List<PlaceSeedImage> images = imagesByKakaoPlaceId.getOrDefault(kakaoPlaceId, List.of());

                rows.add(new PlaceSeedRow(
                        record.get("역명").trim(),
                        record.get("카테고리").trim(),
                        placeName,
                        hashtagTexts,
                        record.get("한 줄 설명").trim(),
                        record.get("주소").trim(),
                        blankToNull(record.get("전화번호")),
                        Double.valueOf(xCoordText),
                        Double.valueOf(yCoordText),
                        kakaoPlaceId,
                        images
                ));
            }
        }
        return rows;
    }

    private String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
