package com.cotato.nextstation.domain.place.dto.response;

import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "관리자 장소 카드")
public record AdminPlaceCardResponse(

        @Schema(description = "장소 ID", example = "101")
        Long placeId,

        @Schema(description = "역의 대표 노선. 대표 노선이 지정되지 않은 역이면 null", nullable = true)
        LineSummaryResponse representativeLine,

        @Schema(description = "역 ID", example = "42")
        Long stationId,

        @Schema(description = "역명", example = "신림역")
        String stationName,

        @Schema(description = "카테고리 코드", example = "CULTURE")
        String categoryCode,

        @Schema(description = "카테고리 표시명", example = "문화공간")
        String categoryName,

        @Schema(description = "장소명", example = "보문숲길도서관")
        String placeName,

        @Schema(description = "해시태그 코드 목록", example = "[\"INDOOR\", \"EXPERIENCE\"]")
        List<String> tags,

        @Schema(description = "한 줄 설명", example = "혼자 조용히 머물기 좋은 동네 도서관")
        String description,

        @Schema(description = "대표 장소 사진 URL. 실제 장소 사진이 없으면 null", nullable = true)
        String imageUrl,

        @Schema(description = "장소 등록 상태", example = "APPROVED")
        PlaceStatus status
) {
}
