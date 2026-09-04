package com.cotato.nextstation.domain.place.dto.response;

import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "관리자 장소 상세 조회 응답")
public record AdminPlaceDetailResponse(

        Long placeId,
        String placeName,

        @Schema(nullable = true)
        LineSummaryResponse representativeLine,

        Long stationId,
        String stationName,

        @Schema(description = "장소 주소", example = "서울 용산구 남영동 72-1")
        String address,

        @Schema(description = "X 좌표(경도)", example = "126.972123")
        Double xCoordinate,

        @Schema(description = "Y 좌표(위도)", example = "37.544321")
        Double yCoordinate,

        @Schema(
                description = "카카오맵 장소 URL",
                example = "https://place.map.kakao.com/123456789",
                nullable = true
        )
        String kakaoPlaceUrl,

        PlaceStatus status,
        String categoryCode,
        String categoryName,
        List<String> tags,
        String description,

        @Schema(description = "실제 장소 사진 URL 목록. sortOrder 오름차순", nullable = false)
        List<String> images,

        @Schema(description = "삭제 사유. 삭제 장소가 아니면 null", nullable = true)
        String deleteReason,

        @Schema(description = "반려 사유. 반려 장소가 아니면 null", nullable = true)
        String rejectReason
) {
}
