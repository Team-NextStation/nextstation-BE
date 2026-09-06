package com.cotato.nextstation.domain.place.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 필터용 역 요약")
public record AdminStationSummaryResponse(

        @Schema(description = "역 ID", example = "42")
        Long stationId,

        @Schema(description = "역명", example = "신림역")
        String stationName
) {
}
