package com.cotato.nextstation.domain.place.repository;

import com.cotato.nextstation.domain.place.entity.PlaceReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.cotato.nextstation.domain.member.repository.MemberRepository.NOT_WITHDRAWN;

public interface PlaceReviewRepository extends JpaRepository<PlaceReview, Long> {

    // 리뷰 텍스트/사진 중 하나라도 있어야 노출 대상이다. 여행일지 작성 시 장소별 리뷰는 선택 입력이라
    // 텍스트도 사진도 없는 리뷰가 저장될 수 있는데, 그런 리뷰는 다른 사람에게 빈 값으로 보이면 안 된다.
    String HAS_CONTENT = "((pr.review IS NOT NULL AND TRIM(pr.review) <> '') " +
            "OR EXISTS (SELECT 1 FROM PlaceReviewImage pri WHERE pri.placeReview = pr))";

    // 장소 상세 조회 - 공개(삭제되지 않은 journal) 기준 리뷰 목록 조회
    // NOT_WITHDRAWN은 별칭 "mem"을 요구하므로, 연관관계로 이미 로드하는 Member도
    // JOIN FETCH j.member mem으로 별칭을 맞춰서 쓴다.
    @Query("SELECT pr FROM PlaceReview pr " +
            "JOIN FETCH pr.journal j " +
            "JOIN FETCH j.member mem " +
            "WHERE pr.place.id = :placeId " +
            "AND " + HAS_CONTENT + " " +
            "AND " + NOT_WITHDRAWN + " " +
            "ORDER BY pr.createdAt DESC")
    List<PlaceReview> findVisibleReviewsByPlaceId(@Param("placeId") Long placeId, Pageable pageable);

    // 좋아요 추가 시 원자적 증가 (동시성 안전)
    @Modifying
    @Query("UPDATE PlaceReview pr SET pr.likeCount = pr.likeCount + 1 WHERE pr.id = :reviewId")
    void incrementLikeCount(@Param("reviewId") Long reviewId);

    // 좋아요 취소 시 원자적 감소 (0 미만으로 안 내려가도록 방어)
    @Modifying
    @Query("UPDATE PlaceReview pr SET pr.likeCount = CASE WHEN pr.likeCount > 0 THEN pr.likeCount - 1 ELSE 0 END WHERE pr.id = :reviewId")
    void decrementLikeCount(@Param("reviewId") Long reviewId);

    /**
     * 탈퇴 회원이 좋아요를 눌러둔 리뷰들의 like_count를 일괄 감소시킨다.
     * <p>
     * place_review_like 행 자체는 지우지 않는다 — 유예 기간 안에 복구(restore)하면
     * {@link #incrementLikeCountForLikesByMember}로 원상 복구해야 하는데, 행이 남아 있어야
     * "이 회원이 어떤 리뷰를 좋아요했었는지"를 다시 알 수 있다. 실제 삭제는 유예 기간이
     * 지나면 WithdrawnMemberCleaner가 처리한다.
     */
    @Modifying
    @Query("UPDATE PlaceReview pr SET pr.likeCount = CASE WHEN pr.likeCount > 0 THEN pr.likeCount - 1 ELSE 0 END " +
            "WHERE EXISTS (SELECT 1 FROM PlaceReviewLike prl " +
            "              WHERE prl.placeReview.id = pr.id AND prl.memberId = :memberId)")
    void decrementLikeCountForLikesByMember(@Param("memberId") Long memberId);

    // 유예 기간 내 복구 시 위 감소분을 되돌린다.
    @Modifying
    @Query("UPDATE PlaceReview pr SET pr.likeCount = pr.likeCount + 1 " +
            "WHERE EXISTS (SELECT 1 FROM PlaceReviewLike prl " +
            "              WHERE prl.placeReview.id = pr.id AND prl.memberId = :memberId)")
    void incrementLikeCountForLikesByMember(@Param("memberId") Long memberId);


    // 장소 리뷰 목록 - 최신순, 최초 페이지
    @Query("SELECT pr FROM PlaceReview pr " +
            "JOIN FETCH pr.journal j " +
            "JOIN FETCH j.member mem " +
            "WHERE pr.place.id = :placeId " +
            "AND " + HAS_CONTENT + " " +
            "AND " + NOT_WITHDRAWN + " " +
            "ORDER BY pr.createdAt DESC, pr.id DESC")
    List<PlaceReview> findByPlaceIdOrderByLatest(@Param("placeId") Long placeId, Pageable pageable);

    // 장소 리뷰 목록 - 최신순, 커서 이후 페이지
    @Query("SELECT pr FROM PlaceReview pr " +
            "JOIN FETCH pr.journal j " +
            "JOIN FETCH j.member mem " +
            "WHERE pr.place.id = :placeId " +
            "AND " + HAS_CONTENT + " " +
            "AND " + NOT_WITHDRAWN + " " +
            "AND (pr.createdAt < :createdAt OR (pr.createdAt = :createdAt AND pr.id < :reviewId)) " +
            "ORDER BY pr.createdAt DESC, pr.id DESC")
    List<PlaceReview> findByPlaceIdOrderByLatestAfterCursor(
            @Param("placeId") Long placeId,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("reviewId") Long reviewId,
            Pageable pageable);

    // 장소 리뷰 목록 - 추천순(likeCount 캐시 컬럼 기준), 최초 페이지
    @Query("SELECT pr FROM PlaceReview pr " +
            "JOIN FETCH pr.journal j " +
            "JOIN FETCH j.member mem " +
            "WHERE pr.place.id = :placeId " +
            "AND " + HAS_CONTENT + " " +
            "AND " + NOT_WITHDRAWN + " " +
            "ORDER BY pr.likeCount DESC, pr.id DESC")
    List<PlaceReview> findByPlaceIdOrderByRecommend(@Param("placeId") Long placeId, Pageable pageable);

    // 장소 리뷰 목록 - 추천순, 커서 이후 페이지
    @Query("SELECT pr FROM PlaceReview pr " +
            "JOIN FETCH pr.journal j " +
            "JOIN FETCH j.member mem " +
            "WHERE pr.place.id = :placeId " +
            "AND " + HAS_CONTENT + " " +
            "AND " + NOT_WITHDRAWN + " " +
            "AND (pr.likeCount < :likeCount OR (pr.likeCount = :likeCount AND pr.id < :reviewId)) " +
            "ORDER BY pr.likeCount DESC, pr.id DESC")
    List<PlaceReview> findByPlaceIdOrderByRecommendAfterCursor(
            @Param("placeId") Long placeId,
            @Param("likeCount") long likeCount,
            @Param("reviewId") Long reviewId,
            Pageable pageable);

    // 장소 리뷰 총 개수 (내용 없는 리뷰는 카운트에서도 제외)
    // j를 where/select 어디서도 참조하지 않으면(journal_id가 not null이라 결과 row 수에
    // 영향을 안 주는 조인이라) Hibernate가 이 조인을 최적화로 제거할 수 있고, 그러면 journal의
    // @SQLRestriction(is_deleted = false)이 적용될 대상 자체가 사라져 필터가 안 걸린다.
    // j.isDeleted = false를 조건에 넣어 j를 실제로 참조하게 하면 조인이 유지되고 필터도 정상 적용된다.
    // (리스트 조회 쿼리들은 JOIN FETCH pr.journal이라 엔티티를 실제로 로드하므로 이 문제가 없다)
    @Query("SELECT COUNT(pr) FROM PlaceReview pr " +
            "JOIN pr.journal j " +
            "JOIN j.member mem " +
            "WHERE pr.place.id = :placeId " +
            "AND j.isDeleted = false " +
            "AND " + HAS_CONTENT + " " +
            "AND " + NOT_WITHDRAWN)
    long countByPlaceId(@Param("placeId") Long placeId);

    // 여행일지에 연결된 장소 리뷰 리스트
    List<PlaceReview> findByJournalId(Long journalId);


    Optional<PlaceReview> findByJournalIdAndPlaceId(Long journalId, Long placeId);

}