package com.cotato.nextstation.domain.place.converter;

import com.cotato.nextstation.domain.place.dto.response.PlaceDetailResponse;
import com.cotato.nextstation.domain.place.dto.response.PlaceInfoResponse;
import com.cotato.nextstation.domain.place.dto.response.PlaceReviewPreviewResponse;
import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.entity.PlaceImage;
import com.cotato.nextstation.domain.place.entity.PlaceReview;
import com.cotato.nextstation.domain.place.entity.PlaceReviewImage;
import com.cotato.nextstation.domain.place.repository.PlaceImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PlaceConverter {

    private static final String KAKAO_PLACE_URL_PREFIX = "https://place.map.kakao.com/";

    private final PlaceImageRepository placeImageRepository;

    // ===== 장소 상세 조회(PlaceDetailResponse)용 =====

    public PlaceDetailResponse toDetailResponse(
            Place place,
            long totalReviewCount,
            List<PlaceImage> placeImages,
            List<PlaceReview> reviews,
            List<PlaceReviewImage> reviewImages
    ) {
        return new PlaceDetailResponse(
                place.getId(),
                place.getPlaceName(),
                place.getDescription(),
                place.getCategory().getName(),
                place.getAddress(),
                place.getContactNumber(),
                toKakaoPlaceUrl(place.getKakaoPlaceId()),
                totalReviewCount,
                toImageUrls(place, placeImages),
                toReviewPreviews(reviews, reviewImages)
        );
    }

    static String toKakaoPlaceUrl(String kakaoPlaceId) {
        return KAKAO_PLACE_URL_PREFIX + kakaoPlaceId;
    }

    // 이미지가 없으면 카테고리 기본 이미지로 폴백
    private List<String> toImageUrls(Place place, List<PlaceImage> placeImages) {
        if (placeImages.isEmpty()) {
            String defaultImageUrl = place.getCategory().getDefaultImageUrl();
            return defaultImageUrl != null ? List.of(defaultImageUrl) : List.of();
        }
        return placeImages.stream()
                .map(PlaceImage::getImageUrl)
                .toList();
    }

    private List<PlaceReviewPreviewResponse> toReviewPreviews(List<PlaceReview> reviews, List<PlaceReviewImage> reviewImages) {
        Map<Long, List<String>> imagesByReviewId = reviewImages.stream()
                .collect(Collectors.groupingBy(
                        image -> image.getPlaceReview().getId(),
                        Collectors.mapping(PlaceReviewImage::getImageUrl, Collectors.toList())
                ));

        return reviews.stream()
                .map(review -> toReviewPreview(review, imagesByReviewId))
                .toList();
    }


    private PlaceReviewPreviewResponse toReviewPreview(PlaceReview review, Map<Long, List<String>> imagesByReviewId) {
        List<String> images = imagesByReviewId.getOrDefault(review.getId(), List.of());
        // 기획상 리뷰 이미지는 1개만 업로드 가능하나,
        // DB는 1:N으로 설계되어 있어 첫 번째 이미지만 응답에 포함한다. 필요 시 추후에 수정.
        String imageUrl = images.isEmpty() ? null : images.get(0);

        return new PlaceReviewPreviewResponse(
                review.getId(),
                review.getJournal().getMember().getId(),
                review.getJournal().getMember().getNickname(),
                review.getJournal().getMember().getProfileImageUrl(),
                review.getReview(),
                imageUrl,
                review.getCreatedAt()
        );
    }

    // ===== 조회 전용 포트(PlaceInfoResponse)용  =====

    public PlaceInfoResponse toPlaceInfoResponse(Place place) {
        String imageUrl = resolveImageUrlsByPlaceId(List.of(place.getId()), List.of(place))
                .get(place.getId());
        return toPlaceInfoResponse(place, imageUrl);
    }


    public List<PlaceInfoResponse> toPlaceInfoResponses(List<Place> places) {
        if (places.isEmpty()) {
        return List.of();
        }
        List<Long> placeIds = places.stream().map(Place::getId).toList();
        Map<Long, String> imageUrlByPlaceId = resolveImageUrlsByPlaceId(placeIds, places);

        return places.stream()
                .map(place -> toPlaceInfoResponse(place, imageUrlByPlaceId.get(place.getId())))
                .toList();
    }

        private Map<Long, String> resolveImageUrlsByPlaceId(List<Long> placeIds, List<Place> places) {
        List<PlaceImage> allImages = placeImageRepository.findByPlaceIdIn(placeIds);
        Map<Long, List<PlaceImage>> imagesByPlaceId = allImages.stream()
                .collect(Collectors.groupingBy(image -> image.getPlace().getId()));

        Map<Long, String> result = new HashMap<>();
        for (Place place : places) {
            List<PlaceImage> images = imagesByPlaceId.getOrDefault(place.getId(), List.of());
            String imageUrl = images.isEmpty()
                    ? place.getCategory().getDefaultImageUrl()
                    : images.get(0).getImageUrl();
            result.put(place.getId(), imageUrl);
        }
        return result;
    }

    private PlaceInfoResponse toPlaceInfoResponse(Place place, String imageUrl) {
        return new PlaceInfoResponse(
                place.getId(),
                place.getPlaceName(),
                place.getDescription(),
                place.getCategory().getCode().name(),
                place.getCategory().getName(),
                imageUrl,
                place.getXCoordinate(),
                place.getYCoordinate()
        );
    }
}