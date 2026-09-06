package com.cotato.nextstation.domain.place.service.query;

import com.cotato.nextstation.domain.member.service.query.AdminGuard;
import com.cotato.nextstation.domain.place.converter.AdminPlaceConverter;
import com.cotato.nextstation.domain.place.dto.request.AdminPlaceCursor;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCardResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceDetailResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceListResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminStationSummaryResponse;
import com.cotato.nextstation.domain.place.enums.CategoryCode;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.exception.PlaceErrorCode;
import com.cotato.nextstation.domain.place.repository.AdminPlaceRepository;
import com.cotato.nextstation.domain.place.repository.AdminPlaceRepository.AdminPlaceDetailView;
import com.cotato.nextstation.domain.place.repository.AdminPlaceRepository.AdminPlaceView;
import com.cotato.nextstation.domain.place.repository.PlaceImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceImageRepository.AdminPlaceImageView;
import com.cotato.nextstation.domain.place.repository.PlaceTagMappingRepository;
import com.cotato.nextstation.domain.place.repository.PlaceTagMappingRepository.AdminPlaceTagView;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import com.cotato.nextstation.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPlaceQueryService {

    private static final int DEFAULT_SIZE = 10;
    private static final int SEARCH_LIMIT = 20;
    private static final String LIKE_ESCAPE = "!";

    private final AdminGuard adminGuard;
    private final AdminPlaceRepository adminPlaceRepository;
    private final PlaceTagMappingRepository placeTagMappingRepository;
    private final PlaceImageRepository placeImageRepository;
    private final AdminPlaceConverter adminPlaceConverter;

    public AdminPlaceListResponse getPlaces(Long memberId, Long lineId, Long stationId,
                                            CategoryCode categoryCode, PlaceStatus status,
                                            String cursor, Integer size) {
        adminGuard.requireAdmin(memberId);

        int pageSize = resolvePageSize(size);
        AdminPlaceCursor cursorData = AdminPlaceCursor.decode(cursor);
        List<AdminPlaceView> fetched = adminPlaceRepository.findAdminPlaces(
                lineId,
                stationId,
                categoryCode != null ? categoryCode.name() : null,
                status != null ? status.name() : null,
                cursorData != null ? cursorData.placeName() : null,
                cursorData != null ? cursorData.placeId() : null,
                PageRequest.of(0, pageSize + 1));

        boolean hasNext = fetched.size() > pageSize;
        List<AdminPlaceView> pageContent = hasNext ? fetched.subList(0, pageSize) : fetched;
        List<AdminPlaceCardResponse> places = toCards(pageContent);

        String nextCursor = null;
        if (hasNext) {
            AdminPlaceView last = pageContent.get(pageContent.size() - 1);
            nextCursor = new AdminPlaceCursor(last.getPlaceName(), last.getPlaceId()).encode();
        }

        boolean firstPage = cursorData == null;
        List<LineSummaryResponse> availableLines = firstPage
                ? adminPlaceConverter.toLineResponses(adminPlaceRepository.findAdminAvailableLines())
                : List.of();
        List<AdminStationSummaryResponse> availableStations = firstPage && lineId != null
                ? adminPlaceConverter.toStationResponses(adminPlaceRepository.findAdminAvailableStations(lineId))
                : List.of();

        return new AdminPlaceListResponse(availableLines, availableStations, places, nextCursor, hasNext);
    }

    public List<AdminPlaceCardResponse> searchPlaces(Long memberId, String keyword) {
        adminGuard.requireAdmin(memberId);

        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<AdminPlaceView> places = adminPlaceRepository.searchAdminPlaces(
                normalized, escapeLikePattern(normalized), PageRequest.of(0, SEARCH_LIMIT));
        return toCards(places);
    }

    public AdminPlaceDetailResponse getPlaceDetail(Long memberId, Long placeId) {
        adminGuard.requireAdmin(memberId);

        AdminPlaceDetailView place = adminPlaceRepository.findAdminPlaceDetail(placeId)
                .orElseThrow(() -> new CustomException(PlaceErrorCode.PLACE_NOT_FOUND));
        Map<Long, List<String>> tagsByPlaceId = loadTags(List.of(placeId));
        Map<Long, List<String>> imagesByPlaceId = loadImages(List.of(placeId));
        return adminPlaceConverter.toDetailResponse(
                place,
                tagsByPlaceId.getOrDefault(placeId, List.of()),
                imagesByPlaceId.getOrDefault(placeId, List.of()));
    }

    private List<AdminPlaceCardResponse> toCards(List<? extends AdminPlaceView> places) {
        if (places.isEmpty()) {
            return List.of();
        }
        List<Long> placeIds = places.stream().map(AdminPlaceView::getPlaceId).toList();
        return adminPlaceConverter.toCardResponses(places, loadTags(placeIds), loadImages(placeIds));
    }

    private Map<Long, List<String>> loadTags(List<Long> placeIds) {
        return placeTagMappingRepository.findAdminTags(placeIds).stream()
                .collect(Collectors.groupingBy(
                        AdminPlaceTagView::getPlaceId,
                        LinkedHashMap::new,
                        Collectors.mapping(AdminPlaceTagView::getTagName, Collectors.toList())));
    }

    private Map<Long, List<String>> loadImages(List<Long> placeIds) {
        return placeImageRepository.findAdminImages(placeIds).stream()
                .collect(Collectors.groupingBy(
                        AdminPlaceImageView::getPlaceId,
                        LinkedHashMap::new,
                        Collectors.mapping(AdminPlaceImageView::getImageUrl, Collectors.toList())));
    }

    private int resolvePageSize(Integer size) {
        return size == null ? DEFAULT_SIZE : size;
    }

    private String escapeLikePattern(String keyword) {
        return keyword.replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }
}
