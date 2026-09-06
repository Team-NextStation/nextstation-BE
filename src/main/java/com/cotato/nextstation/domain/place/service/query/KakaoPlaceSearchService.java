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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KakaoPlaceSearchService {

    private final AdminGuard adminGuard;
    private final StationRepository stationRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final PlaceConverter placeConverter;

    /**
     * 카카오에서 등록할 장소 후보를 찾는다.
     * keyword로 찾을 때 stationId를 함께 주면 역 주변으로 좁히고, address를 주면 그 주소로 찾는다.
     * <p>
     * address가 오면 keyword는 무시한다. 함께 보내면 카카오가 두 토큰을 모두 매칭해 0건이 된다.
     */
    public KakaoPlaceSearchResponse searchKakaoPlaces(Long memberId, Long stationId, String keyword, String address) {
        adminGuard.requireAdmin(memberId);

        if (StringUtils.hasText(address)) {
            return searchByAddress(memberId, address.trim());
        }

        if (!StringUtils.hasText(keyword)) {
            log.warn("검색어와 주소가 모두 비어 있는 카카오 장소 검색 요청: memberId={}", memberId);
            throw new CustomException(GlobalErrorCode.INVALID_REQUEST);
        }
        String trimmedKeyword = keyword.trim();

        List<KakaoKeywordSearchResponse.Document> documents = (stationId == null)
                ? kakaoLocalClient.searchByKeyword(trimmedKeyword)
                : searchAroundStation(stationId, trimmedKeyword);

        log.info("카카오 장소 검색 완료: memberId={}, stationId={}, keyword={}, count={}",
                memberId, stationId, trimmedKeyword, documents.size());

        return placeConverter.toKakaoPlaceSearchResponse(documents);
    }

    // 주소 전용 API(address.json)는 좌표만 주고 카카오 place id를 주지 않아 등록에 쓸 수 없다.
    private KakaoPlaceSearchResponse searchByAddress(Long memberId, String address) {
        List<KakaoKeywordSearchResponse.Document> documents = kakaoLocalClient.searchByKeyword(address);

        log.info("주소로 카카오 장소 검색 완료: memberId={}, address={}, count={}",
                memberId, address, documents.size());

        return placeConverter.toKakaoPlaceSearchResponse(documents);
    }

    // 역명이 상호에 없는 장소가 묻히므로 0건일 때만 역명을 떼고 재검색한다.
    private List<KakaoKeywordSearchResponse.Document> searchAroundStation(Long stationId, String keyword) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 역으로 장소 검색 요청: stationId={}", stationId);
                    return new CustomException(StationErrorCode.STATION_NOT_FOUND);
                });

        List<KakaoKeywordSearchResponse.Document> documents =
                kakaoLocalClient.searchByKeyword(station.getStationName() + " " + keyword);
        if (!documents.isEmpty()) {
            return documents;
        }

        log.info("역명을 붙인 검색이 0건이라 검색어만으로 재검색: stationName={}, keyword={}",
                station.getStationName(), keyword);
        return kakaoLocalClient.searchByKeyword(keyword);
    }
}
