package com.cotato.nextstation.domain.journal.dto.response;

import com.cotato.nextstation.domain.course.dto.response.CoursePlaceInfoResponse;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "여행일지 작성 초기 정보 응답")
public record JournalWriteInfoResponse(
        @Schema(description = "역 이름", example = "보문역")
        String stationName,

        @Schema(description = "코스 이름", example = "보문에 살어리랏다")
        String courseName,

        @Schema(description = "태그 상위 3개", example = "[\"#동네탐색\", \"#자연과함께\", \"#가성비\"]")
        List<String> tags,

        @Schema(description = "코스 내 장소 목록")
        List<PlaceSimpleResponse> places
) {
    @Schema(description = "장소 간략 정보")
    public record PlaceSimpleResponse(
            Long placeId,
            String placeName,
            PlaceStatus placeStatus,
            int orderNum
    ) {}
}
