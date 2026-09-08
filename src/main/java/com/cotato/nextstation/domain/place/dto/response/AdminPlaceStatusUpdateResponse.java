package com.cotato.nextstation.domain.place.dto.response;

import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 상태 변경 결과")
public record AdminPlaceStatusUpdateResponse(

        @Schema(description = "상태가 변경된 장소 ID", example = "301")
        Long placeId,

        @Schema(description = "변경된 상태", example = "REJECTED")
        PlaceStatus status
) {
}
