package com.cotato.nextstation.domain.place.dto.response;

import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "관리자 장소 상세 조회 응답")
public record AdminPlaceDetailResponse(

        @Schema(description = "장소 ID", example = "101")
        Long placeId,

        @Schema(description = "장소명", example = "보문숲길도서관")
        String placeName,

        @Schema(description = "역의 대표 노선. 대표 노선이 지정되지 않은 역이면 null", nullable = true)
        LineSummaryResponse representativeLine,

        @Schema(description = "역 ID", example = "42")
        Long stationId,

        @Schema(description = "역명", example = "신림역")
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

        @Schema(description = "장소 등록 상태", example = "APPROVED")
        PlaceStatus status,

        @Schema(description = "카테고리 코드", example = "CAFE")
        String categoryCode,

        @Schema(description = "카테고리 표시명", example = "카페")
        String categoryName,

        @Schema(description = "해시태그 코드 목록", example = "[\"HOTPLACE\", \"INDOOR\"]")
        List<String> tags,

        @Schema(description = "한 줄 설명", example = "조용히 머물기 좋은 동네 카페")
        String description,

        @Schema(description = "실제 장소 사진 URL 목록. sortOrder 오름차순", nullable = false)
        List<String> images,

        @Schema(description = "삭제 사유. 삭제 장소가 아니면 null", nullable = true)
        String deleteReason,

        @Schema(description = "반려 사유. 반려 장소가 아니면 null", nullable = true)
        String rejectReason
) {
}
