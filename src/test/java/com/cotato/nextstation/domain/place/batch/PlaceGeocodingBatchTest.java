package com.cotato.nextstation.domain.place.batch;

import com.cotato.nextstation.domain.place.batch.PlaceGeocodingBatch.SheetLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 시트에 되써넣을 범위를 헤더에서 유도하는 경로만 검증한다.
 * 카카오 API 호출과 Sheets 반영은 외부 연동이라 여기서 다루지 않는다.
 */
class PlaceGeocodingBatchTest {

    // 2차 수집 이후의 실제 컬럼 순서. 수집 차수와 수정 여부가 앞에 붙어 주소가 M열로 밀렸다.
    private static final List<String> HEADERS = List.of(
            "수집 차수", "수정 여부", "담당자", "진행 상태", "호선", "역명", "카테고리", "장소명",
            "해시태그 1", "해시태그 2", "한 줄 설명", "검수 메모",
            "주소", "전화번호", "x좌표", "y좌표", "카카오맵 URL", "배치 매칭 메모 (BE)");

    @Test
    @DisplayName("헤더 인덱스를 A1 표기 열 문자로 바꾼다")
    void columnLetterConvertsIndexToA1Notation() {
        assertThat(PlaceGeocodingBatch.columnLetter(0)).isEqualTo("A");
        assertThat(PlaceGeocodingBatch.columnLetter(12)).isEqualTo("M");
        assertThat(PlaceGeocodingBatch.columnLetter(25)).isEqualTo("Z");
        assertThat(PlaceGeocodingBatch.columnLetter(26)).isEqualTo("AA");
        assertThat(PlaceGeocodingBatch.columnLetter(27)).isEqualTo("AB");
    }

    @Test
    @DisplayName("컬럼이 앞에 추가돼도 헤더 이름으로 되써넣을 범위를 찾는다")
    void resolveLayoutFindsRangesByHeaderName() {
        SheetLayout layout = PlaceGeocodingBatch.resolveLayout(HEADERS);

        // 주소~카카오맵 URL 5칸과 배치 매칭 메모 1칸. 열 문자를 고정했다면 K:O / P가 나왔을 자리다.
        assertThat(layout.addressBlockRange("장소 데이터", 2)).isEqualTo("장소 데이터!M2:Q2");
        assertThat(layout.matchNoteRange("장소 데이터", 2)).isEqualTo("장소 데이터!R2");
    }

    @Test
    @DisplayName("주소~카카오맵 URL 사이에 다른 컬럼이 있으면 중단한다")
    void resolveLayoutFailsWhenAddressBlockIsNotContiguous() {
        List<String> broken = List.of(
                "역명", "장소명", "주소", "전화번호", "메모", "x좌표", "y좌표", "카카오맵 URL", "배치 매칭 메모 (BE)");

        assertThatThrownBy(() -> PlaceGeocodingBatch.resolveLayout(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("연속");
    }

    @Test
    @DisplayName("배치 매칭 메모 컬럼이 없으면 중단한다")
    void resolveLayoutFailsWhenMatchNoteColumnMissing() {
        List<String> withoutMatchNote = List.of(
                "역명", "장소명", "주소", "전화번호", "x좌표", "y좌표", "카카오맵 URL");

        assertThatThrownBy(() -> PlaceGeocodingBatch.resolveLayout(withoutMatchNote))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("배치 매칭 메모 (BE)");
    }
}
