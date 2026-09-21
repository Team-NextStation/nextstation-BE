package com.cotato.nextstation.domain.course.dto.response;

import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 장소 상세 화면 맨 아래 "이 장소를 포함한 코스" 가로 스크롤 카드.
@Schema(description = "장소를 포함한 코스 카드")
public record PlaceCourseResponse(

        @Schema(description = """
                여행일지 ID. 카드를 누르면 이 id로 여행일지 상세를 연다.
                이 목록은 공개된 여행일지가 있는 코스만 노출하므로 항상 값이 있다.
                """, example = "10")
        Long journalId,

        @Schema(description = "카드에 표시할 이름. journal.title을 사용한다.", example = "주연의 보문역 여행")
        String name,

        @Schema(description = "코스가 속한 역 ID", example = "123")
        Long stationId,

        @Schema(description = "역 이름", example = "보문역")
        String stationName,

        @Schema(description = "카드 배지에 표시할 역의 대표 호선. 대표 호선이 없는 역이면 null", nullable = true)
        LineSummaryResponse line,

        @Schema(description = "코스에 담긴 장소 수", example = "4")
        int placeCount,

        @Schema(description = """
                코스 소요시간 구간. 여행기록 작성 화면의 "코스 시간" 선택지와 같은 값이다.
                `SHORT`=3~4시간, `HALF_DAY`=반나절, `FULL_DAY`=하루종일.
                여행일지에 소요시간이 기록되기 전까지는 장소 수로 추정한 값이 나간다
                (3~4곳=SHORT, 5~7곳=HALF_DAY, 8곳 이상=FULL_DAY).
                """, example = "SHORT")
        String travelDuration,

        @Schema(description = "코스 대표 태그 (최대 2개). 코스에 담긴 장소들의 태그를 집계한 상위 태그다.",
                example = "[\"자연과함께\", \"사진찍기좋은\"]")
        List<String> tags,

        @Schema(description = """
                카드 배경 이미지. 코스의 첫 번째 장소 이미지를 쓰며, 장소 이미지가 없으면
                카테고리 기본 이미지로 대체된다. 둘 다 없으면 null이다(이미지 데이터 입수 전까지는 null).
                """, example = "https://.../place.jpg", nullable = true)
        String imageUrl
) {
}
