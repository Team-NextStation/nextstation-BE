package com.cotato.nextstation.domain.place.dto.response;

import com.cotato.nextstation.domain.place.enums.PlaceStatus;

/**
 * 코스·여행일지처럼 이미 저장된 기록을 복원할 때 사용하는 장소 정보다.
 * 일반 사용자 장소 조회와 달리 비승인 장소도 상태를 포함해 반환한다.
 */
public record HistoricalPlaceInfoResponse(
        Long placeId,
        String placeName,
        String description,
        String categoryCode,
        String categoryName,
        String imageUrl,
        Double xCoordinate,
        Double yCoordinate,
        PlaceStatus placeStatus
) {
}
