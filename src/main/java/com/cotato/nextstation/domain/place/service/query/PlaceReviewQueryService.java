package com.cotato.nextstation.domain.place.service.query;

import com.cotato.nextstation.domain.place.converter.PlaceReviewConverter;
import com.cotato.nextstation.domain.place.dto.response.PlaceReviewListResponse;
import com.cotato.nextstation.domain.place.entity.PlaceReview;
import com.cotato.nextstation.domain.place.enums.PlaceReviewSortType;
import com.cotato.nextstation.domain.place.exception.PlaceErrorCode;
import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewLikeRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.util.CursorData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceReviewQueryService {

    private static final int DEFAULT_SIZE = 10;

    // TODO: 방어적으로 잡은 값. 추후 프론트 실제 요청 사이즈 확인 후 조정 필요.
    private static final int MAX_SIZE = 50;

    private final PlaceRepository placeRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final PlaceReviewLikeRepository placeReviewLikeRepository;
    private final PlaceReviewConverter placeReviewConverter;

    public PlaceReviewListResponse getReviews(Long placeId, PlaceReviewSortType sort, String cursor, Integer size, Long memberId) {
        validatePlaceExists(placeId);

        int pageSize = resolvePageSize(size);

        Pageable pageable = PageRequest.of(0, pageSize + 1); // hasNext 판단용 1개 더 조회

        List<PlaceReview> reviews = fetchReviews(placeId, sort, cursor, pageable, memberId);

        boolean hasNext = reviews.size() > pageSize;
        List<PlaceReview> pageContent = hasNext ? reviews.subList(0, pageSize) : reviews;

        // 최초 조회(cursor 없음)일 때만 totalCount 계산
        Long totalCount = (cursor == null) ? placeReviewRepository.countByPlaceId(placeId, memberId) : null;

        Set<Long> likedReviewIds = resolveLikedReviewIds(memberId, pageContent);
        Map<Long, List<String>> imagesByReviewId = placeReviewConverter.resolveImagesByReviewId(pageContent);

        String nextCursor = hasNext ? buildNextCursor(sort, pageContent.get(pageContent.size() - 1)) : null;

        return placeReviewConverter.toListResponse(totalCount, pageContent, imagesByReviewId, likedReviewIds, nextCursor, hasNext);
    }

    private List<PlaceReview> fetchReviews(Long placeId, PlaceReviewSortType sort, String cursor, Pageable pageable, Long memberId) {
        CursorData cursorData = CursorData.decode(cursor);

        if (cursorData == null) {
            return switch (sort) {
                case RECOMMEND -> placeReviewRepository.findByPlaceIdOrderByRecommend(placeId, memberId, pageable);
                case LATEST -> placeReviewRepository.findByPlaceIdOrderByLatest(placeId, memberId, pageable);
            };
        }

        validateCursorMatchesSort(sort, cursorData);


        return switch (sort) {
            case RECOMMEND -> placeReviewRepository.findByPlaceIdOrderByRecommendAfterCursor(
                    placeId, cursorData.longValue(), cursorData.id(), memberId, pageable);
            case LATEST -> placeReviewRepository.findByPlaceIdOrderByLatestAfterCursor(
                    placeId, cursorData.dateTimeValue(), cursorData.id(), memberId, pageable);
        };
    }

    private void validateCursorMatchesSort(PlaceReviewSortType sort, CursorData cursorData) {
        boolean valid = switch (sort) {
            case RECOMMEND -> cursorData.id() != null && cursorData.longValue() != null && cursorData.dateTimeValue() == null;
            case LATEST -> cursorData.id() != null && cursorData.dateTimeValue() != null && cursorData.longValue() == null;
        };
        if (!valid) {
            throw new CustomException(GlobalErrorCode.INVALID_CURSOR);
        }
    }

    private String buildNextCursor(PlaceReviewSortType sort, PlaceReview lastReview) {
        CursorData cursorData = (sort == PlaceReviewSortType.RECOMMEND)
                ? new CursorData(lastReview.getId(), lastReview.getLikeCount(), null)
                : new CursorData(lastReview.getId(), null, lastReview.getCreatedAt());
        return cursorData.encode();
    }

    private Set<Long> resolveLikedReviewIds(Long memberId, List<PlaceReview> reviews) {
        if (memberId == null || reviews.isEmpty()) {
            return Set.of();
        }
        List<Long> reviewIds = reviews.stream().map(PlaceReview::getId).toList();
        return Set.copyOf(placeReviewLikeRepository.findLikedReviewIdsByMemberId(memberId, reviewIds));
    }

    private void validatePlaceExists(Long placeId) {
        if (!placeRepository.existsById(placeId)) {
            throw new CustomException(PlaceErrorCode.PLACE_NOT_FOUND);
        }
    }

    private int resolvePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new CustomException(GlobalErrorCode.INVALID_PAGE_SIZE);
        }
        return size;
    }
}