package com.cotato.nextstation.domain.place.service.query;

import com.cotato.nextstation.domain.place.converter.PlaceConverter;
import com.cotato.nextstation.domain.place.dto.response.HistoricalPlaceInfoResponse;
import com.cotato.nextstation.domain.place.dto.response.PlaceInfoResponse;
import com.cotato.nextstation.domain.place.dto.response.StationTagCountResponse;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceRepository.HistoricalPlaceView;
import com.cotato.nextstation.domain.place.repository.PlaceTagMappingRepository;
import com.cotato.nextstation.domain.place.repository.PlaceTagMappingRepository.ApprovedPlaceTagView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PlaceInfoQueryServiceTest {

    @InjectMocks
    private PlaceInfoQueryService placeInfoQueryService;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceTagMappingRepository placeTagMappingRepository;

    @Mock
    private PlaceConverter placeConverter;

    @Test
    @DisplayName("placeIds로 장소 정보를 일괄 조회한다")
    void getPlaceInfos_success() {
        // given
        List<Long> placeIds = List.of(1L, 2L);
        List<Place> places = List.of(mock(Place.class), mock(Place.class));
        List<PlaceInfoResponse> expected = List.of(
                new PlaceInfoResponse(1L, "보문숲길도서관", "설명", "CULTURE", "문화공간", "https://image.jpg", 127.1, 37.5)
        );

        given(placeRepository.findAllById(placeIds)).willReturn(places);
        given(placeConverter.toPlaceInfoResponses(places)).willReturn(expected);

        // when
        List<PlaceInfoResponse> result = placeInfoQueryService.getPlaceInfos(placeIds);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("기존 코스·여행일지용 장소 조회는 비승인 상태도 함께 반환한다")
    void getHistoricalPlaceInfos_returnsPlaceStatus() {
        // given
        HistoricalPlaceView view = historicalPlace(1L, PlaceStatus.DELETED);
        given(placeRepository.findHistoricalPlacesByIdIn(List.of(1L))).willReturn(List.of(view));

        // when
        List<HistoricalPlaceInfoResponse> result = placeInfoQueryService.getHistoricalPlaceInfos(List.of(1L));

        // then
        assertThat(result).containsExactly(new HistoricalPlaceInfoResponse(
                1L, "장소1", "설명", "FOOD", "식당", "image.jpg", 127.1, 37.5, PlaceStatus.DELETED));
    }

    @Test
    @DisplayName("태그가 많은 순서대로 상위 3개만 반환한다")
    void getTopTagNames_top3() {
        // given
        List<Long> placeIds = List.of(1L, 2L);

        List<ApprovedPlaceTagView> mappings = List.of(
                approvedTag(1L, "NATURE"), approvedTag(1L, "NATURE"), approvedTag(1L, "NATURE"),
                approvedTag(1L, "BUDGET"), approvedTag(1L, "BUDGET"),
                approvedTag(1L, "SHOPPING"), approvedTag(1L, "INDOOR")
        );

        given(placeTagMappingRepository.findApprovedTagsByPlaceIdIn(placeIds)).willReturn(mappings);

        // when
        List<String> result = placeInfoQueryService.getTopTagNames(placeIds);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo("NATURE");   // count=3, 확정 1위
        assertThat(result.get(1)).isEqualTo("BUDGET");    // count=2, 확정 2위
        assertThat(result.get(2)).isIn("SHOPPING", "INDOOR");  // count=1 동점, 둘 중 하나
    }

    @Test
    @DisplayName("태그가 3개 이하면 있는 만큼만 반환한다")
    void getTopTagNames_lessThanLimit() {
        // given
        List<Long> placeIds = List.of(1L);
        List<ApprovedPlaceTagView> mappings = List.of(approvedTag(1L, "NATURE"));

        given(placeTagMappingRepository.findApprovedTagsByPlaceIdIn(placeIds)).willReturn(mappings);

        // when
        List<String> result = placeInfoQueryService.getTopTagNames(placeIds);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo("NATURE");
    }

    @Test
    @DisplayName("placeId별 태그 이름 목록을 반환한다")
    void getTagNamesByPlace_success() {
        // given
        List<Long> placeIds = List.of(1L, 2L);

        ApprovedPlaceTagView mapping1 = approvedTag(1L, "NATURE");
        ApprovedPlaceTagView mapping2 = approvedTag(1L, "BUDGET");
        ApprovedPlaceTagView mapping3 = approvedTag(2L, "PHOTO_SPOT");

        given(placeTagMappingRepository.findApprovedTagsByPlaceIdIn(placeIds))
                .willReturn(List.of(mapping1, mapping2, mapping3));

        // when
        Map<Long, List<String>> result = placeInfoQueryService.getTagNamesByPlace(placeIds);

        // then
        assertThat(result.get(1L)).containsExactlyInAnyOrder("NATURE", "BUDGET");
        assertThat(result.get(2L)).containsExactly("PHOTO_SPOT");
    }

    @Test
    @DisplayName("빈 목록이면 빈 Map을 반환한다")
    void getTagNamesByPlace_empty() {
        // when
        Map<Long, List<String>> result = placeInfoQueryService.getTagNamesByPlace(List.of());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("역별 태그 집계는 승인 장소 태그만 집계한다")
    void getPlaceCountsByStationForTags_countsApprovedTagsOnly() {
        // given: repository SQL이 APPROVED 조건으로 걸러 반환한 결과만 서비스가 집계한다.
        List<ApprovedPlaceTagView> approvedTags = List.of(
                approvedTag(1L, 10L, "NATURE"),
                approvedTag(2L, 10L, "NATURE"),
                approvedTag(3L, 20L, "BUDGET")
        );
        given(placeTagMappingRepository.findApprovedTagsByTagNameIn(List.of("NATURE", "BUDGET")))
                .willReturn(approvedTags);

        // when
        StationTagCountResponse result = placeInfoQueryService.getPlaceCountsByStationForTags(
                List.of("NATURE", "BUDGET"));

        // then
        assertThat(result.counts()).containsEntry(10L, Map.of("NATURE", 2L));
        assertThat(result.counts()).containsEntry(20L, Map.of("BUDGET", 1L));
    }



    private HistoricalPlaceView historicalPlace(Long placeId, PlaceStatus status) {
        HistoricalPlaceView view = mock(HistoricalPlaceView.class);
        given(view.getPlaceId()).willReturn(placeId);
        given(view.getPlaceName()).willReturn("장소" + placeId);
        given(view.getDescription()).willReturn("설명");
        given(view.getCategoryCode()).willReturn("FOOD");
        given(view.getCategoryName()).willReturn("식당");
        given(view.getImageUrl()).willReturn("image.jpg");
        given(view.getXCoordinate()).willReturn(127.1);
        given(view.getYCoordinate()).willReturn(37.5);
        given(view.getPlaceStatus()).willReturn(status.name());
        return view;
    }

    private ApprovedPlaceTagView approvedTag(Long placeId, String tagName) {
        return approvedTag(placeId, 1L, tagName);
    }

    private ApprovedPlaceTagView approvedTag(Long placeId, Long stationId, String tagName) {
        ApprovedPlaceTagView view = mock(ApprovedPlaceTagView.class);
        lenient().when(view.getPlaceId()).thenReturn(placeId);
        lenient().when(view.getStationId()).thenReturn(stationId);
        lenient().when(view.getTagName()).thenReturn(tagName);
        return view;
    }
}
