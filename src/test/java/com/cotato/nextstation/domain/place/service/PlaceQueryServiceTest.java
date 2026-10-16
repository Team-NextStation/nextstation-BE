package com.cotato.nextstation.domain.place.service;

import com.cotato.nextstation.domain.place.converter.PlaceConverter;
import com.cotato.nextstation.domain.place.dto.response.PlaceDetailResponse;
import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.exception.PlaceErrorCode;
import com.cotato.nextstation.domain.place.repository.PlaceImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.domain.place.service.query.PlaceQueryService;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PlaceQueryServiceTest {

    @InjectMocks
    private PlaceQueryService placeQueryService;

    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private PlaceImageRepository placeImageRepository;
    @Mock
    private PlaceReviewRepository placeReviewRepository;
    @Mock
    private PlaceReviewImageRepository placeReviewImageRepository;
    @Mock
    private PlaceConverter placeConverter;

    @Test
    @DisplayName("존재하지 않는 장소를 조회하면 예외가 발생한다")
    void getPlaceDetail_notFound() {
        // given
        Long placeId = 1L;
        given(placeRepository.findById(placeId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> placeQueryService.getPlaceDetail(placeId, null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(PlaceErrorCode.PLACE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하는 장소를 조회하면 상세 정보를 반환한다")
    void getPlaceDetail_success() {
        // given
        Long placeId = 1L;
        Place place = mock(Place.class);
        given(placeRepository.findById(placeId)).willReturn(Optional.of(place));
        given(placeImageRepository.findByPlaceOrderBySortOrderAsc(place)).willReturn(List.of());
        given(placeReviewRepository.findVisibleReviewsByPlaceId(placeId, null, PageRequest.of(0, 3))).willReturn(List.of());
        given(placeConverter.toDetailResponse(place, 0L, List.of(), List.of(), List.of()))
                .willReturn(new PlaceDetailResponse(placeId, "보문골한옥집", "설명", "식당", "주소", "02-1234-5678",
                        "http://place.map.kakao.com/12345", 0L, List.of(), List.of()));

        // when
        PlaceDetailResponse response = placeQueryService.getPlaceDetail(placeId, null);

        // then
        assertThat(response.placeId()).isEqualTo(placeId);
        assertThat(response.placeName()).isEqualTo("보문골한옥집");
    }
}