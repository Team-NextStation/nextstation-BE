package com.cotato.nextstation.domain.place.converter;

import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCardResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceDetailResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminStationSummaryResponse;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.repository.AdminPlaceRepository.AdminLineView;
import com.cotato.nextstation.domain.place.repository.AdminPlaceRepository.AdminPlaceDetailView;
import com.cotato.nextstation.domain.place.repository.AdminPlaceRepository.AdminPlaceView;
import com.cotato.nextstation.domain.place.repository.AdminPlaceRepository.AdminStationView;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import com.cotato.nextstation.domain.station.entity.LineCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AdminPlaceConverter {

    public List<AdminPlaceCardResponse> toCardResponses(
            List<? extends AdminPlaceView> places,
            Map<Long, List<String>> tagsByPlaceId,
            Map<Long, List<String>> imagesByPlaceId
    ) {
        return places.stream()
                .map(place -> toCardResponse(place,
                        tagsByPlaceId.getOrDefault(place.getPlaceId(), List.of()),
                        imagesByPlaceId.getOrDefault(place.getPlaceId(), List.of())))
                .toList();
    }

    public AdminPlaceDetailResponse toDetailResponse(
            AdminPlaceDetailView place,
            List<String> tags,
            List<String> images
    ) {
        return new AdminPlaceDetailResponse(
                place.getPlaceId(),
                place.getPlaceName(),
                toLineResponse(place),
                place.getStationId(),
                place.getStationName(),
                place.getAddress(),
                place.getXCoordinate(),
                place.getYCoordinate(),
                PlaceConverter.toKakaoPlaceUrl(place.getKakaoPlaceId()),
                PlaceStatus.valueOf(place.getStatus()),
                place.getCategoryCode(),
                place.getCategoryName(),
                tags,
                place.getDescription(),
                images,
                place.getDeleteReason(),
                place.getRejectReason()
        );
    }

    public List<LineSummaryResponse> toLineResponses(List<AdminLineView> lines) {
        return lines.stream()
                .map(line -> new LineSummaryResponse(
                        line.getLineId(), line.getLineName(), LineCode.valueOf(line.getLineCode())))
                .toList();
    }

    public List<AdminStationSummaryResponse> toStationResponses(List<AdminStationView> stations) {
        return stations.stream()
                .map(station -> new AdminStationSummaryResponse(station.getStationId(), station.getStationName()))
                .toList();
    }

    private AdminPlaceCardResponse toCardResponse(AdminPlaceView place, List<String> tags, List<String> images) {
        String imageUrl = images.isEmpty() ? null : images.get(0);
        return new AdminPlaceCardResponse(
                place.getPlaceId(),
                toLineResponse(place),
                place.getStationId(),
                place.getStationName(),
                place.getCategoryCode(),
                place.getCategoryName(),
                place.getPlaceName(),
                tags,
                place.getDescription(),
                imageUrl,
                PlaceStatus.valueOf(place.getStatus())
        );
    }

    private LineSummaryResponse toLineResponse(AdminPlaceView place) {
        if (place.getLineId() == null) {
            return null;
        }
        return new LineSummaryResponse(
                place.getLineId(), place.getLineName(), LineCode.valueOf(place.getLineCode()));
    }
}
