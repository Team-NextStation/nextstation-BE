package com.cotato.nextstation.domain.place.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 사진")
public record AdminPlaceImageResponse(

        @Schema(description = "장소 사진 ID", example = "101")
        Long imageId,

        @Schema(description = "장소 사진 URL", example = "https://bucket.s3.ap-northeast-2.amazonaws.com/images/static/places/8137464/image.jpg")
        String imageUrl
) {
}
