package com.cotato.nextstation.domain.place.dto.response;

import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 태그 선택지")
public record AdminPlaceTagResponse(
        @Schema(description = "요청에 사용할 태그 코드", example = "HOTPLACE")
        PlaceTagName tagName,

        @Schema(description = "화면 표시명", example = "핫플레이스")
        String label
) {
}
