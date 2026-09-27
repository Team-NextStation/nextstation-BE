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
import com.cotato.nextstation.global.util.CursorData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PlaceReviewQueryServiceTest {

    @InjectMocks
    private PlaceReviewQueryService placeReviewQueryService;

    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private PlaceReviewRepository placeReviewRepository;
    @Mock
    private PlaceReviewLikeRepository placeReviewLikeRepository;
    @Mock
    private PlaceReviewConverter placeReviewConverter;


    private PlaceReview mockReview(Long id, long likeCount, LocalDateTime createdAt) {
        PlaceReview review = mock(PlaceReview.class);
        lenient().when(review.getId()).thenReturn(id);
        lenient().when(review.getLikeCount()).thenReturn(likeCount);
        lenient().when(review.getCreatedAt()).thenReturn(createdAt);
        return review;
    }


    @Test
    @DisplayName("존재하지 않는 장소를 조회하면 예외가 발생한다")
    void getReviews_placeNotFound() {
        // given
        Long placeId = 999L;
        given(placeRepository.existsById(placeId)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> placeReviewQueryService.getReviews(placeId, PlaceReviewSortType.RECOMMEND, null, null, null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(PlaceErrorCode.PLACE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("최초 조회(커서 없음) 시 추천순으로 리뷰를 가져오고 totalCount를 함께 반환한다")
    void getReviews_recommend_firstPage() {
        // given
        Long placeId = 1L;
        given(placeRepository.existsById(placeId)).willReturn(true);

        PlaceReview review1 = mockReview(501L, 30L, LocalDateTime.now());
        PlaceReview review2 = mockReview(502L, 20L, LocalDateTime.now());
        List<PlaceReview> reviews = List.of(review1, review2);

        given(placeReviewRepository.findByPlaceIdOrderByRecommend(eq(placeId), any(), any()))
                .willReturn(reviews);
        given(placeReviewRepository.countByPlaceId(placeId, 1L)).willReturn(24L);
        given(placeReviewLikeRepository.findLikedReviewIdsByMemberId(any(), anyList()))
                .willReturn(List.of());
        given(placeReviewConverter.resolveImagesByReviewId(reviews))
                .willReturn(Map.of());

        PlaceReviewListResponse expected = new PlaceReviewListResponse(24L, List.of(), null, false);
        given(placeReviewConverter.toListResponse(eq(24L), eq(reviews), any(), any(), any(), eq(false)))
                .willReturn(expected);

        // when
        PlaceReviewListResponse result = placeReviewQueryService.getReviews(placeId, PlaceReviewSortType.RECOMMEND, null, 10, 1L);

        // then
        assertThat(result).isEqualTo(expected);
        verify(placeReviewRepository).findByPlaceIdOrderByRecommend(eq(placeId), any(), any());
        verify(placeReviewRepository).countByPlaceId(placeId, 1L);
        verify(placeReviewRepository, never()).findByPlaceIdOrderByRecommendAfterCursor(any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("커서가 주어진 요청(다음 페이지)은 totalCount를 계산하지 않고 null로 반환한다")
    void getReviews_withCursor_totalCountIsNull() {
        // given
        Long placeId = 1L;
        given(placeRepository.existsById(placeId)).willReturn(true);

        CursorData cursorData = new CursorData(501L, 30L, null);
        String cursor = cursorData.encode();

        List<PlaceReview> reviews = List.of(mockReview(502L, 20L, LocalDateTime.now()));

        given(placeReviewRepository.findByPlaceIdOrderByRecommendAfterCursor(eq(placeId), eq(30L), eq(501L), any(), any()))
                .willReturn(reviews);
        given(placeReviewLikeRepository.findLikedReviewIdsByMemberId(any(), anyList()))
                .willReturn(List.of());
        given(placeReviewConverter.resolveImagesByReviewId(reviews))
                .willReturn(Map.of());
        given(placeReviewConverter.toListResponse(eq((Long) null), eq(reviews), any(), any(), any(), eq(false)))
                .willReturn(new PlaceReviewListResponse(null, List.of(), null, false));

        // when
        PlaceReviewListResponse result = placeReviewQueryService.getReviews(placeId, PlaceReviewSortType.RECOMMEND, cursor, 10, 1L);

        // then
        assertThat(result.totalCount()).isNull();
        verify(placeReviewRepository, never()).countByPlaceId(any(), any());
        verify(placeReviewRepository).findByPlaceIdOrderByRecommendAfterCursor(eq(placeId), eq(30L), eq(501L), any(), any());
        verify(placeReviewRepository, never()).findByPlaceIdOrderByRecommend(any(), any(), any());
    }

    @Test
    @DisplayName("조회 결과가 요청 size보다 많으면 hasNext가 true이고 nextCursor가 생성된다")
    void getReviews_hasNext_true() {
        // given
        Long placeId = 1L;
        given(placeRepository.existsById(placeId)).willReturn(true);

        // size=2 요청 시 서비스는 3개(size+1)를 조회함
        PlaceReview review1 = mockReview(501L, 30L, LocalDateTime.now());
        PlaceReview review2 = mockReview(502L, 20L, LocalDateTime.now());
        PlaceReview review3 = mockReview(503L, 10L, LocalDateTime.now());
        List<PlaceReview> reviews = List.of(review1, review2, review3);

        given(placeReviewRepository.findByPlaceIdOrderByRecommend(eq(placeId), any(), any()))
                .willReturn(reviews);
        given(placeReviewRepository.countByPlaceId(placeId, 1L)).willReturn(3L);
        given(placeReviewLikeRepository.findLikedReviewIdsByMemberId(any(), anyList()))
                .willReturn(List.of());
        given(placeReviewConverter.resolveImagesByReviewId(anyList()))
                .willReturn(Map.of());
        given(placeReviewConverter.toListResponse(any(), anyList(), any(), any(), any(), eq(true)))
                .willAnswer(invocation -> new PlaceReviewListResponse(
                        invocation.getArgument(0), List.of(), invocation.getArgument(4), true));

        // when
        PlaceReviewListResponse result = placeReviewQueryService.getReviews(placeId, PlaceReviewSortType.RECOMMEND, null, 2, 1L);

        // then
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("조회 결과가 요청 size 이하이면 hasNext가 false이고 nextCursor는 null이다")
    void getReviews_hasNext_false() {
        // given
        Long placeId = 1L;
        given(placeRepository.existsById(placeId)).willReturn(true);

        PlaceReview review1 = mockReview(501L, 30L, LocalDateTime.now());
        List<PlaceReview> reviews = List.of(review1);

        given(placeReviewRepository.findByPlaceIdOrderByRecommend(eq(placeId), any(), any()))
                .willReturn(reviews);
        given(placeReviewRepository.countByPlaceId(placeId, 1L)).willReturn(1L);
        given(placeReviewLikeRepository.findLikedReviewIdsByMemberId(any(), anyList()))
                .willReturn(List.of());
        given(placeReviewConverter.resolveImagesByReviewId(reviews))
                .willReturn(Map.of());
        given(placeReviewConverter.toListResponse(eq(1L), eq(reviews), any(), any(), eq(null), eq(false)))
                .willReturn(new PlaceReviewListResponse(1L, List.of(), null, false));

        // when
        PlaceReviewListResponse result = placeReviewQueryService.getReviews(placeId, PlaceReviewSortType.RECOMMEND, null, 10, 1L);

        // then
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("최신순 정렬이면 findByPlaceIdOrderByLatest를 호출한다")
    void getReviews_latestSort() {
        // given
        Long placeId = 1L;
        given(placeRepository.existsById(placeId)).willReturn(true);

        List<PlaceReview> reviews = List.of(mockReview(501L, 30L, LocalDateTime.now()));

        given(placeReviewRepository.findByPlaceIdOrderByLatest(eq(placeId), any(), any()))
                .willReturn(reviews);
        given(placeReviewRepository.countByPlaceId(placeId, 1L)).willReturn(1L);
        given(placeReviewLikeRepository.findLikedReviewIdsByMemberId(any(), anyList()))
                .willReturn(List.of());
        given(placeReviewConverter.resolveImagesByReviewId(reviews))
                .willReturn(Map.of());
        given(placeReviewConverter.toListResponse(any(), eq(reviews), any(), any(), any(), eq(false)))
                .willReturn(new PlaceReviewListResponse(1L, List.of(), null, false));

        // when
        placeReviewQueryService.getReviews(placeId, PlaceReviewSortType.LATEST, null, 10, 1L);

        // then
        verify(placeReviewRepository).findByPlaceIdOrderByLatest(eq(placeId), any(), any());
    }

    @Test
    @DisplayName("최신순 커서 조회 시 findByPlaceIdOrderByLatestAfterCursor를 호출한다")
    void getReviews_latestSort_withCursor() {
        // given
        Long placeId = 1L;
        given(placeRepository.existsById(placeId)).willReturn(true);

        LocalDateTime cursorCreatedAt = LocalDateTime.of(2026, 7, 10, 10, 0);
        CursorData cursorData = new CursorData(501L, null, cursorCreatedAt);
        String cursor = cursorData.encode();

        List<PlaceReview> reviews = List.of(mockReview(502L, 10L, LocalDateTime.now()));

        given(placeReviewRepository.findByPlaceIdOrderByLatestAfterCursor(eq(placeId), eq(cursorCreatedAt), eq(501L), any(), any()))
                .willReturn(reviews);
        given(placeReviewLikeRepository.findLikedReviewIdsByMemberId(any(), anyList()))
                .willReturn(List.of());
        given(placeReviewConverter.resolveImagesByReviewId(reviews))
                .willReturn(Map.of());
        given(placeReviewConverter.toListResponse(any(), eq(reviews), any(), any(), any(), eq(false)))
                .willReturn(new PlaceReviewListResponse(null, List.of(), null, false));

        // when
        placeReviewQueryService.getReviews(placeId, PlaceReviewSortType.LATEST, cursor, 10, 1L);

        // then
        verify(placeReviewRepository).findByPlaceIdOrderByLatestAfterCursor(eq(placeId), eq(cursorCreatedAt), eq(501L), any(), any());
        verify(placeReviewRepository, never()).findByPlaceIdOrderByLatest(any(), any(), any());
    }

    @Test
    @DisplayName("비로그인 사용자(memberId=null)는 좋아요 여부 조회를 하지 않는다")
    void getReviews_anonymousUser_skipsLikeLookup() {
        // given
        Long placeId = 1L;
        given(placeRepository.existsById(placeId)).willReturn(true);

        List<PlaceReview> reviews = List.of(mockReview(501L, 30L, LocalDateTime.now()));

        given(placeReviewRepository.findByPlaceIdOrderByRecommend(eq(placeId), any(), any()))
                .willReturn(reviews);
        given(placeReviewRepository.countByPlaceId(placeId, null)).willReturn(1L);
        given(placeReviewConverter.resolveImagesByReviewId(reviews))
                .willReturn(Map.of());
        given(placeReviewConverter.toListResponse(any(), eq(reviews), any(), eq(Set.of()), any(), eq(false)))
                .willReturn(new PlaceReviewListResponse(1L, List.of(), null, false));

        // when
        placeReviewQueryService.getReviews(placeId, PlaceReviewSortType.RECOMMEND, null, 10, null);

        // then
        verify(placeReviewLikeRepository, never()).findLikedReviewIdsByMemberId(any(), anyList());
    }
}