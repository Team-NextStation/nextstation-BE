package com.cotato.nextstation.domain.place.service.query;

import com.cotato.nextstation.domain.member.service.query.AdminGuard;
import com.cotato.nextstation.domain.place.client.KakaoLocalClient;
import com.cotato.nextstation.domain.place.client.dto.KakaoKeywordSearchResponse;
import com.cotato.nextstation.domain.place.converter.PlaceConverter;
import com.cotato.nextstation.domain.place.dto.response.KakaoPlaceSearchResponse;
import com.cotato.nextstation.domain.station.entity.Station;
import com.cotato.nextstation.domain.station.exception.StationErrorCode;
import com.cotato.nextstation.domain.station.repository.StationRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class KakaoPlaceSearchServiceTest {

    @InjectMocks
    private KakaoPlaceSearchService kakaoPlaceSearchService;

    @Mock
    private AdminGuard adminGuard;

    @Mock
    private StationRepository stationRepository;

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    // 카카오 응답 매핑까지 함께 검증하려고 실제 구현을 쓴다. 검색 경로에서는 이미지 저장소를 타지 않는다.
    @Spy
    private PlaceConverter placeConverter = new PlaceConverter(null);

    private static final Long ADMIN_ID = 1L;
    private static final Long STATION_ID = 12L;

    private KakaoKeywordSearchResponse.Document document(String id, String placeName) {
        return new KakaoKeywordSearchResponse.Document(
                id, placeName, "서울 강남구 역삼동 858", "서울 강남구 강남대로 390",
                "02-1234-5678", "127.0276", "37.4979");
    }

    private Station station() {
        return Station.builder().stationName("강남역").build();
    }

    @Test
    @DisplayName("stationId가 없으면 검색어를 그대로 한 번만 검색한다")
    void searchKakaoPlaces_withoutStation() {
        given(kakaoLocalClient.searchByKeyword("스타벅스")).willReturn(List.of(document("8137464", "스타벅스 강남역점")));

        KakaoPlaceSearchResponse response = kakaoPlaceSearchService.searchKakaoPlaces(ADMIN_ID, null, "  스타벅스  ", null);

        assertThat(response.places()).hasSize(1);
        KakaoPlaceSearchResponse.KakaoPlaceCandidate candidate = response.places().get(0);
        assertThat(candidate.kakaoPlaceId()).isEqualTo("8137464");
        assertThat(candidate.address()).isEqualTo("서울 강남구 강남대로 390");
        assertThat(candidate.xCoordinate()).isEqualTo(127.0276);
        assertThat(candidate.kakaoPlaceUrl()).isEqualTo("https://place.map.kakao.com/8137464");

        then(stationRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("stationId가 있으면 역명을 붙여 검색하고, 결과가 있으면 재검색하지 않는다")
    void searchKakaoPlaces_withStation() {
        given(stationRepository.findById(STATION_ID)).willReturn(Optional.of(station()));
        given(kakaoLocalClient.searchByKeyword("강남역 스타벅스")).willReturn(List.of(document("8137464", "스타벅스 강남역점")));

        KakaoPlaceSearchResponse response = kakaoPlaceSearchService.searchKakaoPlaces(ADMIN_ID, STATION_ID, "스타벅스", null);

        assertThat(response.places()).hasSize(1);
        then(kakaoLocalClient).should(never()).searchByKeyword("스타벅스");
    }

    @Test
    @DisplayName("역명을 붙인 검색이 0건이면 검색어만으로 다시 검색한다")
    void searchKakaoPlaces_retryWithoutStationName() {
        given(stationRepository.findById(STATION_ID)).willReturn(Optional.of(station()));
        given(kakaoLocalClient.searchByKeyword("강남역 크래커")).willReturn(List.of());
        given(kakaoLocalClient.searchByKeyword("크래커")).willReturn(List.of(document("123", "크래커")));

        KakaoPlaceSearchResponse response = kakaoPlaceSearchService.searchKakaoPlaces(ADMIN_ID, STATION_ID, "크래커", null);

        assertThat(response.places()).hasSize(1);
        assertThat(response.places().get(0).placeName()).isEqualTo("크래커");
    }

    @Test
    @DisplayName("재검색까지 0건이면 예외가 아니라 빈 목록을 반환한다")
    void searchKakaoPlaces_emptyResult() {
        given(stationRepository.findById(STATION_ID)).willReturn(Optional.of(station()));
        given(kakaoLocalClient.searchByKeyword(anyString())).willReturn(List.of());

        KakaoPlaceSearchResponse response = kakaoPlaceSearchService.searchKakaoPlaces(ADMIN_ID, STATION_ID, "없는가게", null);

        assertThat(response.places()).isEmpty();
    }

    @Test
    @DisplayName("도로명 주소와 전화번호가 빈 문자열이면 지번 주소로 대체하고 전화번호는 null이다")
    void searchKakaoPlaces_blankFields() {
        KakaoKeywordSearchResponse.Document document = new KakaoKeywordSearchResponse.Document(
                "123", "동네 분식", "서울 강남구 역삼동 1-1", "", "", "127.0", "37.0");
        given(kakaoLocalClient.searchByKeyword("동네 분식")).willReturn(List.of(document));

        KakaoPlaceSearchResponse response = kakaoPlaceSearchService.searchKakaoPlaces(ADMIN_ID, null, "동네 분식", null);

        KakaoPlaceSearchResponse.KakaoPlaceCandidate candidate = response.places().get(0);
        assertThat(candidate.address()).isEqualTo("서울 강남구 역삼동 1-1");
        assertThat(candidate.contactNumber()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 역이면 404이고 카카오를 호출하지 않는다")
    void searchKakaoPlaces_stationNotFound() {
        given(stationRepository.findById(STATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> kakaoPlaceSearchService.searchKakaoPlaces(ADMIN_ID, STATION_ID, "스타벅스", null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", StationErrorCode.STATION_NOT_FOUND);

        then(kakaoLocalClient).should(never()).searchByKeyword(anyString());
    }

    @Test
    @DisplayName("검색어가 공백뿐이면 400이고 카카오를 호출하지 않는다")
    void searchKakaoPlaces_blankKeyword() {
        assertThatThrownBy(() -> kakaoPlaceSearchService.searchKakaoPlaces(ADMIN_ID, null, "   ", null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.INVALID_REQUEST);

        then(kakaoLocalClient).should(never()).searchByKeyword(anyString());
    }

    @Test
    @DisplayName("관리자가 아니면 카카오를 호출하지 않고 막는다")
    void searchKakaoPlaces_notAdmin() {
        willThrow(new CustomException(GlobalErrorCode.FORBIDDEN)).given(adminGuard).requireAdmin(ADMIN_ID);

        assertThatThrownBy(() -> kakaoPlaceSearchService.searchKakaoPlaces(ADMIN_ID, null, "스타벅스", null))
                .isInstanceOf(CustomException.class);

        then(kakaoLocalClient).should(never()).searchByKeyword(anyString());
    }

    @Test
    @DisplayName("address가 있으면 주소로만 검색하고 역은 조회하지 않는다")
    void searchKakaoPlaces_byAddress() {
        given(kakaoLocalClient.searchByKeyword("서울 용산구 원효로1가 48"))
                .willReturn(List.of(document("111", "크래커")));

        KakaoPlaceSearchResponse response = kakaoPlaceSearchService.searchKakaoPlaces(
                ADMIN_ID, STATION_ID, "크래커", "  서울 용산구 원효로1가 48  ");

        assertThat(response.places()).hasSize(1);
        assertThat(response.places().get(0).kakaoPlaceId()).isEqualTo("111");
        // 화면 05는 장소명으로 실패한 뒤 단계라 역명도 장소명도 쓰지 않는다
        then(stationRepository).should(never()).findById(anyLong());
        then(kakaoLocalClient).should(never()).searchByKeyword("크래커");
    }

    @Test
    @DisplayName("keyword가 비어도 address가 있으면 검색한다")
    void searchKakaoPlaces_addressWithoutKeyword() {
        given(kakaoLocalClient.searchByKeyword("서울 용산구 원효로1가 48"))
                .willReturn(List.of(document("111", "크래커")));

        KakaoPlaceSearchResponse response =
                kakaoPlaceSearchService.searchKakaoPlaces(ADMIN_ID, null, null, "서울 용산구 원효로1가 48");

        assertThat(response.places()).hasSize(1);
    }

    @Test
    @DisplayName("keyword와 address가 모두 비면 400이고 카카오를 호출하지 않는다")
    void searchKakaoPlaces_bothBlank() {
        assertThatThrownBy(() -> kakaoPlaceSearchService.searchKakaoPlaces(ADMIN_ID, STATION_ID, null, "  "))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.INVALID_REQUEST);

        then(kakaoLocalClient).should(never()).searchByKeyword(anyString());
    }
}
