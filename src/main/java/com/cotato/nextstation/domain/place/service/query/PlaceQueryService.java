package com.cotato.nextstation.domain.place.service.query;

import com.cotato.nextstation.domain.place.converter.PlaceConverter;
import com.cotato.nextstation.domain.place.dto.response.PlaceDetailResponse;
import com.cotato.nextstation.domain.place.dto.response.PlaceInfoResponse;
import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.entity.PlaceImage;
import com.cotato.nextstation.domain.place.entity.PlaceReview;
import com.cotato.nextstation.domain.place.entity.PlaceReviewImage;
import com.cotato.nextstation.domain.place.exception.PlaceErrorCode;
import com.cotato.nextstation.domain.place.repository.PlaceImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceQueryService {

    private static final int REVIEW_PREVIEW_SIZE = 3;

    private final PlaceRepository placeRepository;
    private final PlaceImageRepository placeImageRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final PlaceReviewImageRepository placeReviewImageRepository;
    private final PlaceConverter placeConverter;

    public PlaceDetailResponse getPlaceDetail(Long placeId, Long memberId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(PlaceErrorCode.PLACE_NOT_FOUND));

        // 비로그인(memberId == null)이면 NOT_BLOCKED_BY_VIEWER 조건이 필터를 건너뛴다.
        long totalReviewCount = placeReviewRepository.countByPlaceId(placeId, memberId);

        List<PlaceImage> placeImages = placeImageRepository.findByPlaceOrderBySortOrderAsc(place);
        List<PlaceReview> reviews = placeReviewRepository.findVisibleReviewsByPlaceId(
                placeId, memberId, PageRequest.of(0, REVIEW_PREVIEW_SIZE)
        );

        List<Long> reviewIds = reviews.stream().map(PlaceReview::getId).toList();
        List<PlaceReviewImage> reviewImages = placeReviewImageRepository.findByPlaceReviewIdIn(reviewIds);
        return placeConverter.toDetailResponse(place, totalReviewCount, placeImages, reviews, reviewImages);
    }

    public List<PlaceInfoResponse> getPlaceInfos(List<Long> placeIds) {
        List<Place> places = placeRepository.findAllById(placeIds);
        return placeConverter.toPlaceInfoResponses(places);
    }

    public List<PlaceInfoResponse> getPlacesByStation(Long stationId) {
        List<Place> places = placeRepository.findByStationId(stationId);
        return placeConverter.toPlaceInfoResponses(places);  // 같은 변환 메서드 재사용
    }

}