package com.cotato.nextstation.domain.place.init;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * place-images.csv를 읽어 장소에 붙이는 경로만 검증한다.
 * DB 저장은 PlaceSeedWriter 책임이라 여기서는 리포지토리를 쓰지 않는다.
 */
class PlaceSeederTest {

    private static final String BASE_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/images/static/places";

    private final PlaceSeeder placeSeeder = new PlaceSeeder(null, null);

    @Test
    @DisplayName("이미지 목록 csv를 카카오 place id별로 순서 컬럼 기준 정렬해 묶는다")
    void readImagesGroupsByKakaoPlaceIdInSortOrder() throws IOException {
        // 2번 사진이 먼저 오도록 일부러 섞어둔다. 행 순서가 아니라 "순서" 컬럼을 따라야 한다.
        String csv = """
                카카오 place id,순서,이미지 URL,출처
                1584284345,2,%s/1584284345/2.jpg,한국관광공사
                1584284345,1,%s/1584284345/1.jpg,한국관광공사
                27334082,1,%s/27334082/1.jpg,
                """.formatted(BASE_URL, BASE_URL, BASE_URL);

        Map<String, List<PlaceSeedImage>> result = placeSeeder.readImages(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result).hasSize(2);
        // 첫 장이 썸네일(sortOrder 0)이 되므로 순서가 뒤집히면 대표 이미지가 바뀐다.
        assertThat(result.get("1584284345")).containsExactly(
                new PlaceSeedImage(BASE_URL + "/1584284345/1.jpg", "한국관광공사"),
                new PlaceSeedImage(BASE_URL + "/1584284345/2.jpg", "한국관광공사"));
        // 출처가 비어 있으면 null — 직접 촬영본은 출처표시 의무가 없다.
        assertThat(result.get("27334082")).containsExactly(
                new PlaceSeedImage(BASE_URL + "/27334082/1.jpg", null));
    }

    @Test
    @DisplayName("이미지 목록 csv가 없으면 빈 맵을 준다")
    void readImagesReturnsEmptyWhenNoFile() throws IOException {
        assertThat(placeSeeder.readImages(new byte[0])).isEmpty();
    }

    @Test
    @DisplayName("컬럼이 앞에 추가돼도 헤더 이름으로 값을 매핑한다")
    void readSeedRowsMapsByHeaderName() throws IOException {
        // 2차 수집 이후의 컬럼 순서. 수집 차수와 수정 여부가 앞에 붙어 이후 컬럼이 두 칸씩 밀렸다.
        String csv = """
                수집 차수,수정 여부,담당자,진행 상태,호선,역명,카테고리,장소명,해시태그 1,해시태그 2,한 줄 설명,검수 메모,주소,전화번호,x좌표,y좌표,카카오맵 URL,배치 매칭 메모 (BE)
                1차,o,서은주,검수 완료,2호선,잠실나루역,식당,송봉칼국수,가성비,실내위주,따뜻한 칼국수,,서울 송파구 신천동 7,02-1234-5678,127.099280,37.518885,https://place.map.kakao.com/1584284345,
                """;

        List<PlaceSeedRow> rows = placeSeeder.readSeedRows(csv.getBytes(StandardCharsets.UTF_8), Map.of());

        assertThat(rows).hasSize(1);
        PlaceSeedRow row = rows.get(0);
        assertThat(row.stationName()).isEqualTo("잠실나루역");
        assertThat(row.categoryText()).isEqualTo("식당");
        assertThat(row.placeName()).isEqualTo("송봉칼국수");
        assertThat(row.hashtagTexts()).containsExactly("가성비", "실내위주");
        assertThat(row.description()).isEqualTo("따뜻한 칼국수");
        assertThat(row.address()).isEqualTo("서울 송파구 신천동 7");
        assertThat(row.contactNumber()).isEqualTo("02-1234-5678");
        assertThat(row.xCoordinate()).isEqualTo(127.099280);
        assertThat(row.yCoordinate()).isEqualTo(37.518885);
    }

    @Test
    @DisplayName("카카오맵 URL이 없는 행은 시딩하지 않는다")
    void readSeedRowsSkipsRowWithoutKakaoPlaceUrl() throws IOException {
        String csv = """
                수집 차수,수정 여부,담당자,진행 상태,호선,역명,카테고리,장소명,해시태그 1,해시태그 2,한 줄 설명,검수 메모,주소,전화번호,x좌표,y좌표,카카오맵 URL,배치 매칭 메모 (BE)
                2차,,서은주,검수 완료,2호선,잠실나루역,식당,카카오맵에 없는 곳,가성비,,설명,,서울 송파구 신천동 7,,127.099280,37.518885,,NO_RESULT
                """;

        assertThat(placeSeeder.readSeedRows(csv.getBytes(StandardCharsets.UTF_8), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("카카오맵 URL의 마지막 세그먼트를 place id로 쓴다")
    void extractKakaoPlaceIdUsesLastSegment() {
        assertThat(placeSeeder.extractKakaoPlaceId("https://place.map.kakao.com/1584284345"))
                .isEqualTo("1584284345");
    }

    @Test
    @DisplayName("카카오맵 URL이 없는 장소는 사진을 붙일 키가 없다")
    void extractKakaoPlaceIdReturnsNullWhenUrlMissing() {
        assertThat(placeSeeder.extractKakaoPlaceId(null)).isNull();
        assertThat(placeSeeder.extractKakaoPlaceId("   ")).isNull();
    }
}
