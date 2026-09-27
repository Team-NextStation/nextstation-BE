package com.cotato.nextstation.domain.place.service.query;

import com.cotato.nextstation.domain.place.converter.PlaceConverter;
import com.cotato.nextstation.domain.place.dto.response.HistoricalPlaceInfoResponse;
import com.cotato.nextstation.domain.place.dto.response.PlaceInfoResponse;
import com.cotato.nextstation.domain.place.dto.response.StationTagCountResponse;
import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import com.cotato.nextstation.domain.place.exception.PlaceErrorCode;
import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceRepository.HistoricalPlaceView;
import com.cotato.nextstation.domain.place.repository.PlaceTagMappingRepository;
import com.cotato.nextstation.domain.place.repository.PlaceTagMappingRepository.ApprovedPlaceTagView;
import com.cotato.nextstation.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceInfoQueryService {

    private static final int TOP_TAG_LIMIT = 3;

    private final PlaceRepository placeRepository;
    private final PlaceTagMappingRepository placeTagMappingRepository;
    private final PlaceConverter placeConverter;

    public PlaceInfoResponse getPlaceInfo(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(PlaceErrorCode.PLACE_NOT_FOUND));
        return placeConverter.toPlaceInfoResponse(place);
    }

    public List<PlaceInfoResponse> getPlaceInfos(List<Long> placeIds) {
        List<Place> places = placeRepository.findAllById(placeIds);
        return placeConverter.toPlaceInfoResponses(places);
    }

    public List<HistoricalPlaceInfoResponse> getHistoricalPlaceInfos(List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        return placeRepository.findHistoricalPlacesByIdIn(placeIds).stream()
                .map(this::toHistoricalPlaceInfo)
                .toList();
    }

    public List<String> getTopTagNames(List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        List<ApprovedPlaceTagView> mappings = placeTagMappingRepository.findApprovedTagsByPlaceIdIn(placeIds);

        Map<String, Long> tagCountMap = mappings.stream()
                .collect(Collectors.groupingBy(
                        ApprovedPlaceTagView::getTagName,
                        Collectors.counting()
                ));

        return tagCountMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_TAG_LIMIT)
                .map(Map.Entry::getKey)
                .toList();
    }

    public StationTagCountResponse getPlaceCountsByStationForTags(List<String> tags) {
        if (tags.isEmpty()) {
            return new StationTagCountResponse(Map.of());
        }
        List<String> tagNames = tags.stream()
                .map(PlaceTagName::valueOf)
                .map(PlaceTagName::name)
                .toList();

        List<ApprovedPlaceTagView> mappings = placeTagMappingRepository.findApprovedTagsByTagNameIn(tagNames);

        Map<Long, Map<String, Long>> result = mappings.stream()
                .collect(Collectors.groupingBy(
                        ApprovedPlaceTagView::getStationId,
                        Collectors.groupingBy(
                                ApprovedPlaceTagView::getTagName,
                                Collectors.counting()
                        )
                ));

        return new StationTagCountResponse(result);
    }

    public Map<Long, List<String>> getTagNamesByPlace(List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        List<ApprovedPlaceTagView> mappings = placeTagMappingRepository.findApprovedTagsByPlaceIdIn(placeIds);

        return mappings.stream()
                .collect(Collectors.groupingBy(
                        ApprovedPlaceTagView::getPlaceId,
                        Collectors.mapping(
                                ApprovedPlaceTagView::getTagName,
                                Collectors.toList()
                        )
                ));
    }

    private HistoricalPlaceInfoResponse toHistoricalPlaceInfo(HistoricalPlaceView place) {
        return new HistoricalPlaceInfoResponse(
                place.getPlaceId(),
                place.getPlaceName(),
                place.getDescription(),
                place.getCategoryCode(),
                place.getCategoryName(),
                place.getImageUrl(),
                place.getXCoordinate(),
                place.getYCoordinate(),
                PlaceStatus.valueOf(place.getPlaceStatus())
        );
    }

}
