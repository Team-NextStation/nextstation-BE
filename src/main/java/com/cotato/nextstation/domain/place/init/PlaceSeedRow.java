package com.cotato.nextstation.domain.place.init;

import java.util.List;

record PlaceSeedRow(
        String stationName,
        String categoryText,
        String placeName,
        List<String> hashtagTexts,
        String description,
        String address,
        String contactNumber,
        Double xCoordinate,
        Double yCoordinate,
        String kakaoPlaceId,
        List<PlaceSeedImage> images
) {
}
