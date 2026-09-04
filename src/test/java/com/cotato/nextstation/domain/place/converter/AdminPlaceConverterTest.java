package com.cotato.nextstation.domain.place.converter;

import com.cotato.nextstation.domain.place.dto.response.AdminPlaceDetailResponse;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.repository.AdminPlaceRepository.AdminPlaceDetailView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AdminPlaceConverterTest {

    private final AdminPlaceConverter adminPlaceConverter = new AdminPlaceConverter();

    @Test
    @DisplayName("관리자 장소 상세에 주소와 좌표 및 카카오맵 URL을 매핑한다")
    void toDetailResponse_mapsLocationInformation() {
        AdminPlaceDetailView place = mock(AdminPlaceDetailView.class);
        given(place.getPlaceId()).willReturn(7L);
        given(place.getPlaceName()).willReturn("테스트 장소");
        given(place.getLineId()).willReturn(2L);
        given(place.getLineName()).willReturn("2호선");
        given(place.getLineCode()).willReturn("LINE_2");
        given(place.getStationId()).willReturn(10L);
        given(place.getStationName()).willReturn("신림역");
        given(place.getStatus()).willReturn(PlaceStatus.APPROVED.name());
        given(place.getCategoryCode()).willReturn("CAFE");
        given(place.getCategoryName()).willReturn("카페");
        given(place.getDescription()).willReturn("설명");
        given(place.getAddress()).willReturn("서울 용산구 남영동 72-1");
        given(place.getXCoordinate()).willReturn(126.972123);
        given(place.getYCoordinate()).willReturn(37.544321);
        given(place.getKakaoPlaceUrl()).willReturn("https://place.map.kakao.com/123456789");

        AdminPlaceDetailResponse response = adminPlaceConverter.toDetailResponse(
                place, List.of("INDOOR"), List.of("image-url"));

        assertThat(response.address()).isEqualTo("서울 용산구 남영동 72-1");
        assertThat(response.xCoordinate()).isEqualTo(126.972123);
        assertThat(response.yCoordinate()).isEqualTo(37.544321);
        assertThat(response.kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/123456789");
    }
}
