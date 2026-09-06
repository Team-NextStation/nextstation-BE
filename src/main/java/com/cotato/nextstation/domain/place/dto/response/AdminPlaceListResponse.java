package com.cotato.nextstation.domain.place.dto.response;

import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "관리자 장소 목록 조회 응답")
public record AdminPlaceListResponse(

        @Schema(description = "대표 노선 필터 선택지. 첫 페이지에서만 제공")
        List<LineSummaryResponse> availableLines,

        @Schema(description = "선택한 대표 노선의 역 필터 선택지. 첫 페이지에서만 제공")
        List<AdminStationSummaryResponse> availableStations,

        @Schema(description = "장소 카드 목록")
        List<AdminPlaceCardResponse> places,

        @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", nullable = true)
        String nextCursor,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext
) {
}
