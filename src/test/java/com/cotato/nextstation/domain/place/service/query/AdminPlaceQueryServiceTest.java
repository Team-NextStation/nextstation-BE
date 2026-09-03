package com.cotato.nextstation.domain.place.service.query;

import com.cotato.nextstation.domain.member.service.query.AdminGuard;
import com.cotato.nextstation.domain.place.converter.AdminPlaceConverter;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCardResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceDetailResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceListResponse;
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
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminPlaceQueryServiceTest {

    private static final Long ADMIN_ID = 1L;

    @InjectMocks
    private AdminPlaceQueryService adminPlaceQueryService;

    @Mock
    private AdminGuard adminGuard;

    @Mock
    private AdminPlaceRepository adminPlaceRepository;

    @Mock
    private PlaceTagMappingRepository placeTagMappingRepository;

    @Mock
    private PlaceImageRepository placeImageRepository;

    @Mock
    private AdminPlaceConverter adminPlaceConverter;

    @Test
    @DisplayName("목록은 페이지에 포함된 장소의 태그와 이미지를 각각 한 번에 조회한다")
    void getPlaces_loadsCardCollectionsInBatch() {
        AdminPlaceView first = placeView(1L, "가게");
        AdminPlaceView extra = mock(AdminPlaceView.class);
        given(adminPlaceRepository.findAdminPlaces(
                eq(3L), eq(10L), eq("CAFE"), eq("APPROVED"),
                eq(null), eq(null), any(Pageable.class)))
                .willReturn(List.of(first, extra));
        given(adminPlaceRepository.findAdminAvailableLines()).willReturn(List.of());
        given(adminPlaceRepository.findAdminAvailableStations(3L)).willReturn(List.of());

        AdminPlaceTagView tag = mock(AdminPlaceTagView.class);
        given(tag.getPlaceId()).willReturn(1L);
        given(tag.getTagName()).willReturn("INDOOR");
        given(placeTagMappingRepository.findAdminTags(List.of(1L))).willReturn(List.of(tag));

        AdminPlaceImageView image = mock(AdminPlaceImageView.class);
        given(image.getPlaceId()).willReturn(1L);
        given(image.getImageUrl()).willReturn("image-1");
        given(placeImageRepository.findAdminImages(List.of(1L))).willReturn(List.of(image));
        given(adminPlaceConverter.toCardResponses(any(), any(), any())).willReturn(List.of());

        AdminPlaceListResponse response = adminPlaceQueryService.getPlaces(
                ADMIN_ID, 3L, 10L, CategoryCode.CAFE, PlaceStatus.APPROVED, null, 1);

        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isNotBlank();
        verify(adminGuard).requireAdmin(ADMIN_ID);
        verify(placeTagMappingRepository).findAdminTags(List.of(1L));
        verify(placeImageRepository).findAdminImages(List.of(1L));
    }

    @Test
    @DisplayName("빈 검색어는 관리자 검증 후 조회 쿼리 없이 빈 목록을 반환한다")
    void searchPlaces_blankKeywordReturnsEmpty() {
        List<AdminPlaceCardResponse> response = adminPlaceQueryService.searchPlaces(ADMIN_ID, "   ");

        assertThat(response).isEmpty();
        verify(adminGuard).requireAdmin(ADMIN_ID);
        verify(adminPlaceRepository, never()).searchAdminPlaces(any(), any(), any());
        verify(placeTagMappingRepository, never()).findAdminTags(any());
        verify(placeImageRepository, never()).findAdminImages(any());
    }

    @Test
    @DisplayName("관리자 상세는 SQLRestriction과 무관한 전용 조회 결과로 태그와 전체 이미지를 조립한다")
    void getPlaceDetail_returnsAllStatusPlace() {
        AdminPlaceDetailView place = mock(AdminPlaceDetailView.class);
        given(adminPlaceRepository.findAdminPlaceDetail(7L)).willReturn(Optional.of(place));
        given(placeTagMappingRepository.findAdminTags(List.of(7L))).willReturn(List.of());
        given(placeImageRepository.findAdminImages(List.of(7L))).willReturn(List.of());

        AdminPlaceDetailResponse expected = mock(AdminPlaceDetailResponse.class);
        given(adminPlaceConverter.toDetailResponse(place, List.of(), List.of())).willReturn(expected);

        AdminPlaceDetailResponse response = adminPlaceQueryService.getPlaceDetail(ADMIN_ID, 7L);

        assertThat(response).isSameAs(expected);
        verify(adminGuard).requireAdmin(ADMIN_ID);
    }

    @Test
    @DisplayName("관리자 상세에서도 장소가 없으면 404를 반환한다")
    void getPlaceDetail_notFound() {
        given(adminPlaceRepository.findAdminPlaceDetail(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminPlaceQueryService.getPlaceDetail(ADMIN_ID, 999L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.PLACE_NOT_FOUND);
    }

    private AdminPlaceView placeView(Long placeId, String placeName) {
        AdminPlaceView view = mock(AdminPlaceView.class);
        given(view.getPlaceId()).willReturn(placeId);
        given(view.getPlaceName()).willReturn(placeName);
        return view;
    }
}
