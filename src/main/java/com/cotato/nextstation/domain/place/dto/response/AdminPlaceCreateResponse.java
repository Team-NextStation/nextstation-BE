package com.cotato.nextstation.domain.place.dto.response;

import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 등록 결과")
public record AdminPlaceCreateResponse(

        @Schema(description = "생성된 장소 ID", example = "301")
        Long placeId,

        @Schema(description = "저장된 상태. 검수 전이라 항상 PENDING이다", example = "PENDING")
        PlaceStatus status
) {
}
