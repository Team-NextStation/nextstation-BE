package com.cotato.nextstation.domain.course.repository;

import com.cotato.nextstation.domain.course.entity.Course;
import com.cotato.nextstation.domain.station.entity.LineCode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.cotato.nextstation.domain.member.repository.MemberRepository.NOT_WITHDRAWN;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // 다중 삭제 대상 조회. memberId로 걸러서 남의 코스는 애초에 대상에서 빠진다(부분 성공 허용).
    List<Course> findAllByMemberIdAndIdIn(Long memberId, List<Long> ids);

    // 코스 확인 화면. 헤더의 역 이름까지 한 번에 가져온다.
    // memberId를 조건에 넣어 남의 코스는 애초에 조회되지 않는다(존재 여부도 알리지 않는다).
    // 공개 조건은 걸지 않는다. 본인 코스는 일지를 안 썼거나 비공개여도 보여야 한다.
    // Course는 stationId만 들고 있어(연관관계 미매핑) Station을 id로 ad-hoc 조인한다.
    // 화면 상단 "Next Station" 배지가 호선에 따라 달라져서 대표 호선까지 함께 가져온다.
    // 뽑기 대상이 아닌 역은 대표 호선이 없을 수 있어 LEFT JOIN으로 둔다(목록 카드와 같은 기준).
    // shareToken은 이 화면의 공유하기 버튼이 링크를 만드는 데 쓴다.
    // CourseDetailView를 함께 쓰는 다른 두 쿼리(findPublicCourseDetail/findShareCourseDetail)는
    // 이 필드를 선택하지 않는다 — 그쪽 컨버터는 getShareToken()을 호출하지 않아 문제되지 않는다.
    @Query("SELECT c.id AS courseId, c.name AS name, c.shareToken AS shareToken, " +
            "s.id AS stationId, s.stationName AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Station s ON s.id = c.stationId " +
            "LEFT JOIN s.drawLine l " +
            "WHERE c.id = :courseId AND c.memberId = :memberId")
    Optional<CourseDetailView> findMyCourseDetail(@Param("memberId") Long memberId,
                                                    @Param("courseId") Long courseId);

    // "내 코스로 만들기" 준비 화면. 위 코스 확인 화면과 같은 구성이되 대상이 타인의 공개 코스다.
    // 소유자 조건 대신 공개 조건(Journal INNER JOIN + isPublic)을 걸어, 비공개 코스는 조회되지 않는다.
    // 존재 여부와 공개 여부를 한 쿼리로 판정하므로 결과가 비면 그대로 404다.
    // 이름 입력칸 초기값은 journal.title을 쓴다. 이 쿼리는 항상 공개(=journal 존재) 코스만 조회하므로
    // null 걱정이 없다. 본인 코스 확인(findMyCourseDetail)은 이 결정과 무관해 그대로 course.name을 쓴다.
    @Query("SELECT c.id AS courseId, j.title AS name, " +
            "s.id AS stationId, s.stationName AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Station s ON s.id = c.stationId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "LEFT JOIN s.drawLine l " +
            "WHERE c.id = :courseId AND j.isPublic = true AND " + NOT_WITHDRAWN)
    Optional<CourseDetailView> findPublicCourseDetail(@Param("courseId") Long courseId);

    // 공유 링크로 조회하는 화면. 소유자/공개 여부를 따지지 않는다.
    // courseId 대신 추측 불가능한 shareToken으로 조회해, 링크를 모르는 사람은 다른 사람의
    // 코스를 ID만 바꿔가며 열람할 수 없다.
    // 삭제된 코스는 Course의 @SQLRestriction으로 자동 제외된다.
    // 작성자가 탈퇴(WITHDRAWN)했으면 공유 링크로도 열람할 수 없게 막는다. 탈퇴 전에 이미
    // 뿌려진 링크는 소유자 조건이 없는 이 쿼리만 통과하면 계속 열리므로, 다른 코스 조회
    // 경로와 마찬가지로 여기도 걸러야 한다.
    @Query("SELECT c.id AS courseId, c.name AS name, " +
            "s.id AS stationId, s.stationName AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Station s ON s.id = c.stationId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "LEFT JOIN s.drawLine l " +
            "WHERE c.shareToken = :shareToken AND " + NOT_WITHDRAWN)
    Optional<CourseDetailView> findShareCourseDetail(@Param("shareToken") String shareToken);

    // 여행일지 삭제 시 참조를 끊을 코스를 찾는다.
    // 일지는 삭제되면서 member_stamp_id를 비우므로(재작성 허용), 그 뒤에는 일지에서 코스를
    // 역산할 수 없다. 그래서 삭제 시점에 journalId로 직접 찾아야 한다.
    // 삭제된 코스는 @SQLRestriction으로 조회되지 않는데, 어차피 둘러보기 대상이 아니라 무시해도 된다.
    Optional<Course> findByJournalId(Long journalId);

    // 좋아요 가능한 코스인지 확인한다.
    // Journal을 INNER JOIN 하므로 journalId가 NULL인 코스는 자동 제외되고,
    // 삭제된 코스/일지는 각 엔티티의 @SQLRestriction으로 걸러진다.
    @Query("SELECT COUNT(c) > 0 FROM Course c " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "WHERE c.id = :courseId AND j.isPublic = true AND " + NOT_WITHDRAWN)
    boolean existsPublicById(@Param("courseId") Long courseId);

    /**
     * 코스 상세를 열 때 조회수를 올린다.
     * <p>
     * like_count와 같은 이유로 DB에서 직접 증가시킨다. 엔티티를 읽어 +1 하면
     * 동시 조회 시 한쪽 증가분이 유실된다.
     * <p>
     * 본인이 자기 코스를 열었을 때는 올리지 않는다. 저장 탭에서 자기 코스를 드나들 때마다
     * 오르면 인기순(view_count + like_count×2)이 자주 열어본 사람 순으로 부풀려진다.
     * 비로그인 조회는 본인일 수 없으므로 항상 올린다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Course c SET c.viewCount = c.viewCount + 1 " +
            "WHERE c.id = :courseId AND (:viewerMemberId IS NULL OR c.memberId <> :viewerMemberId)")
    int increaseViewCount(@Param("courseId") Long courseId,
                          @Param("viewerMemberId") Long viewerMemberId);

    /**
     * 증가 직후 최신 조회수를 같은(REQUIRES_NEW) 트랜잭션 안에서 읽어오기 위한 조회다.
     * <p>
     * 호출부(코스 상세 조회)의 바깥 트랜잭션은 REPEATABLE READ 스냅샷을 이미 떠 놓은 상태라,
     * 거기서 다시 조회하면 이 UPDATE가 커밋됐어도 증가 전 값이 보인다. 그래서 증가를 수행한
     * 바로 그 트랜잭션 안에서 값을 읽어 반환해야 한다.
     */
    @Query("SELECT c.viewCount FROM Course c WHERE c.id = :courseId")
    Integer findViewCountById(@Param("courseId") Long courseId);

    // like_count는 DB에서 직접 증감시킨다.
    // 엔티티를 읽어 +1 하면 동시 좋아요 시 한쪽 증가분이 유실된다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Course c SET c.likeCount = c.likeCount + 1 WHERE c.id = :courseId")
    void increaseLikeCount(@Param("courseId") Long courseId);

    // 동시 취소로 음수가 되지 않도록 0보다 클 때만 감소시킨다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Course c SET c.likeCount = c.likeCount - 1 WHERE c.id = :courseId AND c.likeCount > 0")
    void decreaseLikeCount(@Param("courseId") Long courseId);

    /**
     * 다중/전체 취소용. 실제로 좋아요돼 있는 코스만 좋아요 수를 줄이고, 지운 개수를 돌려준다.
     * <p>
     * 벌크 삭제는 어떤 코스가 지워졌는지 알려주지 않으므로(MySQL에 DELETE ... RETURNING이 없다),
     * 대신 EXISTS로 "지금 좋아요가 남아 있는 코스"만 골라 감소시킨다.
     * 이미 취소된 코스가 목록에 섞여 있어도 좋아요 수가 과다 감소하지 않는다.
     * 반드시 삭제보다 먼저 실행해야 한다. 삭제 후에는 EXISTS가 전부 거짓이 된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Course c SET c.likeCount = c.likeCount - 1 " +
            "WHERE c.id IN :courseIds AND c.likeCount > 0 " +
            "AND EXISTS (SELECT 1 FROM CourseLike cs " +
            "            WHERE cs.courseId = c.id AND cs.memberId = :memberId)")
    int decreaseLikeCountAll(@Param("memberId") Long memberId,
                             @Param("courseIds") Collection<Long> courseIds);

    /**
     * 탈퇴 회원이 좋아요를 눌러둔 코스들의 like_count를 일괄 감소시킨다.
     * <p>
     * course_like 행 자체는 지우지 않는다 — 유예 기간 안에 복구(restore)하면
     * {@link #increaseLikeCountForLikesByMember}로 원상 복구해야 하는데, 행이 남아 있어야
     * "이 회원이 어떤 코스를 좋아요했었는지"를 다시 알 수 있다. 실제 삭제는 유예 기간이
     * 지나면 WithdrawnMemberCleaner가 처리한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Course c SET c.likeCount = c.likeCount - 1 " +
            "WHERE c.likeCount > 0 " +
            "AND EXISTS (SELECT 1 FROM CourseLike cs " +
            "            WHERE cs.courseId = c.id AND cs.memberId = :memberId)")
    void decreaseLikeCountForLikesByMember(@Param("memberId") Long memberId);

    // 유예 기간 내 복구 시 위 감소분을 되돌린다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Course c SET c.likeCount = c.likeCount + 1 " +
            "WHERE EXISTS (SELECT 1 FROM CourseLike cs " +
            "              WHERE cs.courseId = c.id AND cs.memberId = :memberId)")
    void increaseLikeCountForLikesByMember(@Param("memberId") Long memberId);

    // 역별 인기 공개 코스 조회
    // 인기순 = view_count + like_count*2, 동률이면 최신순(j.createdAt, 여행일지 작성 시점)으로 2차 정렬
    // 코스는 일지 없이 먼저 생성될 수 있어 최신순 기준은 코스 저장 시점(c.createdAt)이 아니라
    // 여행일지 작성 시점(j.createdAt)으로 잡는다.
    // 공개 노출 조건: journal_id가 있고 그 여행일지가 공개인 코스만
    // Course는 journalId를 Long으로만 들고 있어 Journal을 id로 ad-hoc 조인한다
    // INNER JOIN이라 journalId가 NULL인 코스는 자동 제외된다.
    // 카드 제목(name)은 journal.title을 쓴다. 공개 코스만 조회하므로 null 걱정이 없다.
    // 스탬프 도메인이 CourseQueryService.getPopularCoursesByStation을 통해
    // 이 값을 그대로 소비한다 — 정렬 기준을 바꿀 때 함께 공유해야 한다.
    @Query("SELECT c.id AS courseId, j.title AS name, c.viewCount AS viewCount, c.likeCount AS likeCount " +
            "FROM Course c " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "WHERE c.stationId = :stationId AND j.isPublic = true AND " + NOT_WITHDRAWN + " " +
            "ORDER BY (c.viewCount + c.likeCount * 2) DESC, j.createdAt DESC, c.id DESC")
    List<PopularCourseView> findPopularPublicCoursesByStationId(@Param("stationId") Long stationId, Pageable pageable);

    // 내가 만든 코스 목록 (최신순). 카드에 필요한 역/대표 호선까지 한 번에 가져온다(코스마다 조회하면 N+1).
    // Course는 stationId만 들고 있어(연관관계 미매핑) Station을 id로 ad-hoc 조인한다.
    // 대표 호선이 없는 역도 있을 수 있어 LEFT JOIN으로 둔다.
    // 본인 코스이므로 공개 여부는 걸지 않는다. 삭제된 코스는 @SQLRestriction이 제외한다.
    // 호선/역 필터는 둘 다 선택 사항이라 파라미터가 null이면 조건을 건너뛴다.
    //
    // 호선 필터는 역이 속한 호선 전체(StationLine)가 아니라 대표 호선(draw_line) 기준으로 판단한다.
    // 카드 배지가 대표 호선 하나만 보여주는 이상 소속 호선 전체로
    // 거르면 "2호선 탭인데 카드는 1호선"처럼 필터-배지 불일치가 생기고, 프론트가 이를 배지 기준으로
    // 재필터링하면 서버가 계산한 hasNext/커서와도 어긋난다. 환승역 코스가 대표 아닌 호선 탭에서
    // 안 보이는 것(예: 동묘앞역 코스가 6호선 탭엔 안 뜸)은 감수하기로 함(design-decisions.md 참고).
    @Query("SELECT c.id AS courseId, c.name AS name, c.createdAt AS createdAt, " +
            "s.id AS stationId, s.stationName AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Station s ON s.id = c.stationId " +
            "LEFT JOIN s.drawLine l " +
            "WHERE c.memberId = :memberId " +
            "AND (:lineId IS NULL OR l.id = :lineId) " +
            "AND (:stationId IS NULL OR s.id = :stationId) " +
            "ORDER BY c.createdAt DESC, c.id DESC")
    List<MyCourseView> findMyCourses(@Param("memberId") Long memberId,
                                     @Param("lineId") Long lineId,
                                     @Param("stationId") Long stationId,
                                     Pageable pageable);

    // 다음 페이지. 생성 시각이 같을 수 있어 id를 tie-breaker로 함께 비교한다.
    @Query("SELECT c.id AS courseId, c.name AS name, c.createdAt AS createdAt, " +
            "s.id AS stationId, s.stationName AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Station s ON s.id = c.stationId " +
            "LEFT JOIN s.drawLine l " +
            "WHERE c.memberId = :memberId " +
            "AND (:lineId IS NULL OR l.id = :lineId) " +
            "AND (:stationId IS NULL OR s.id = :stationId) " +
            "AND (c.createdAt < :createdAt OR (c.createdAt = :createdAt AND c.id < :courseId)) " +
            "ORDER BY c.createdAt DESC, c.id DESC")
    List<MyCourseView> findMyCoursesAfterCursor(@Param("memberId") Long memberId,
                                                @Param("lineId") Long lineId,
                                                @Param("stationId") Long stationId,
                                                @Param("createdAt") LocalDateTime createdAt,
                                                @Param("courseId") Long courseId,
                                                Pageable pageable);

    // 맞춤추천 "안 가본 역" 우선순위용. 회원이 완료(스탬프)한 코스들의 역을 distinct로 가져온다.
    // MemberStamp는 courseId만 들고 있어(연관관계 미매핑) Course를 id로 ad-hoc 조인한다.
    @Query("SELECT DISTINCT c.stationId FROM MemberStamp ms " +
            "JOIN Course c ON c.id = ms.courseId " +
            "WHERE ms.memberId = :memberId")
    List<Long> findVisitedStationIds(@Param("memberId") Long memberId);

    // 다른 회원 프로필 - 공개 코스 개수. 여행일지가 있고 그 일지가 공개인 코스만 센다.
    // Course는 journalId를 Long으로만 들고 있어(연관관계 미매핑) Journal을 id로 ad-hoc 조인한다.
    @Query("SELECT COUNT(c) FROM Course c " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "WHERE c.memberId = :memberId AND j.isPublic = true AND " + NOT_WITHDRAWN)
    long countPublicCoursesByMemberId(@Param("memberId") Long memberId);

    // 다른 회원의 공개코스 탭 - 공개 코스 목록(최신순). 카드에 필요한 역/대표 호선·journalId·좋아요 수까지
    // 한 번에 가져온다(코스마다 조회하면 N+1). journalId는 코스 상세 라우트가 이 값 기준으로 열려
    // 카드 이동에 필수다. imageUrl은 이 쿼리로 가져오지 않고, 서비스에서 journalId를 모아
    // JournalCardQueryService로 배치 조회한다(썸네일이 Journal 쪽 데이터라 여기서 조인하지 않는다).
    // 카드 제목도 마찬가지로 journal.title을 쓴다. 공개 코스만 조회하므로 null 걱정이 없다.
    // 조회 대상 회원의 것이 아니라 요청자가 로그인만 하면 되므로 소유권 검증은 하지 않는다.
    // 최신순 기준은 코스 저장 시점(c.createdAt)이 아니라 여행일지 작성 시점(j.createdAt)이다 —
    // 코스는 일지 없이 먼저 생성될 수 있어 저장 시점 기준이면 한참 뒤에 일지를 쓴 코스가
    // 방금 저장만 해둔 코스보다 순위에서 밀릴 수 있다.
    @Query("SELECT c.id AS courseId, c.journalId AS journalId, j.title AS name, j.createdAt AS createdAt, " +
            "c.likeCount AS likeCount, " +
            "s.id AS stationId, s.stationName AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Station s ON s.id = c.stationId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "LEFT JOIN s.drawLine l " +
            "WHERE c.memberId = :memberId AND j.isPublic = true AND " + NOT_WITHDRAWN + " " +
            "ORDER BY j.createdAt DESC, c.id DESC")
    List<MemberCourseCardView> findPublicCoursesByMemberId(@Param("memberId") Long memberId, Pageable pageable);

    // 다음 페이지. 생성 시각이 같을 수 있어 id를 tie-breaker로 함께 비교한다.
    @Query("SELECT c.id AS courseId, c.journalId AS journalId, j.title AS name, j.createdAt AS createdAt, " +
            "c.likeCount AS likeCount, " +
            "s.id AS stationId, s.stationName AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Station s ON s.id = c.stationId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "LEFT JOIN s.drawLine l " +
            "WHERE c.memberId = :memberId AND j.isPublic = true AND " + NOT_WITHDRAWN + " " +
            "AND (j.createdAt < :createdAt OR (j.createdAt = :createdAt AND c.id < :courseId)) " +
            "ORDER BY j.createdAt DESC, c.id DESC")
    List<MemberCourseCardView> findPublicCoursesByMemberIdAfterCursor(@Param("memberId") Long memberId,
                                                               @Param("createdAt") LocalDateTime createdAt,
                                                               @Param("courseId") Long courseId,
                                                               Pageable pageable);

    // 내 코스가 하나라도 있는 호선. 코스 없는 호선 칩을 비활성화하는 데 쓴다.
    // 현재 필터와 무관하게 전체 기준으로 조회해야 필터를 바꿔 끼울 수 있다.
    // 페이징으로는 전체 목록을 볼 수 없어 서버가 따로 알려준다.
    // 호선 필터와 같은 기준(대표 호선)으로 조회해야 한다. 기준이 다르면
    // "칩은 활성인데 필터를 걸면 결과가 없는" 불일치가 생긴다.
    @Query("SELECT DISTINCT l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Station s ON s.id = c.stationId " +
            "JOIN s.drawLine l " +
            "WHERE c.memberId = :memberId " +
            "ORDER BY l.name")
    List<LineView> findAvailableLines(@Param("memberId") Long memberId);

    // 특정 장소를 담고 있는 공개 코스를 인기순으로 조회한다 (장소 상세 화면 하단).
    // 카드에 필요한 역·대표 호선을 함께 가져온다(코스마다 조회하면 N+1).
    // 노출 조건과 인기순 공식은 위 역별 인기 코스와 같다.
    // 카드 제목은 journal.title을 쓴다. 공개 코스만 조회하므로 null 걱정이 없다.
    // 동률 tie-break도 코스 저장 시점(c.createdAt)이 아니라 여행일지 작성 시점(j.createdAt)이다 —
    // 코스는 일지 없이 먼저 생성될 수 있어 저장 시점 기준이면 한참 뒤에 일지를 쓴 코스가
    // 방금 저장만 해둔 코스보다 순위에서 밀릴 수 있다.
    @Query("SELECT c.id AS courseId, c.journalId AS journalId, j.title AS name, " +
            "s.id AS stationId, s.stationName AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM CoursePlace cp " +
            "JOIN Course c ON c.id = cp.courseId " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Station s ON s.id = c.stationId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "LEFT JOIN s.drawLine l " +
            "WHERE cp.placeId = :placeId AND j.isPublic = true AND " + NOT_WITHDRAWN + " " +
            "ORDER BY (c.viewCount + c.likeCount * 2) DESC, j.createdAt DESC, c.id DESC")
    List<PlaceCourseView> findPopularPublicCoursesByPlaceId(@Param("placeId") Long placeId, Pageable pageable);

    /**
     * 둘러보기 코스 목록 - 최신순. 노선따라 둘러보기와 코스 검색이 같은 조회를 쓴다.
     * <p>
     * 공개 조건(journal_id가 있고 그 일지가 공개)·필터·검색이 모두 같아서 하나로 합쳤다.
     * 화면마다 쿼리를 복붙하면 공개 조건이 바뀔 때 한 곳을 빠뜨려 비공개 코스가 새어 나간다.
     * <p>
     * 필터는 전부 선택 사항이라 파라미터가 null이면 조건을 건너뛴다.
     * 호선 필터는 저장 탭과 같은 기준으로 대표 호선(draw_line)을 본다.
     * 역이 속한 호선 전체(StationLine)로 걸렀던 이전 방식은 카드 배지(대표 호선만 표시)와
     * 필터 칩이 어긋나는 문제가 있었다. design-decisions.md 참고.
     * <p>
     * 검색 대상은 코스 이름과 역명뿐이다("동네" 제외 확정). 역명은 역 검색과 같은 규칙으로
     * 꼬리의 "역"을 떼고 비교하며, 검색어 쪽도 서비스에서 같은 규칙으로 다듬어 넘긴다.
     * <p>
     * 커서(createdAt·courseId)가 null이면 첫 페이지다.
     * <p>
     * 커서·정렬 기준의 createdAt은 c.createdAt(코스 저장 시점)이 아니라 j.createdAt(여행일지 작성
     * 시점)이다 — 코스는 일지 없이 먼저 생성될 수 있어 코스 저장 시점 기준이면 한참 뒤에
     * 일지를 쓴 코스가 방금 저장만 해둔 코스보다 밀릴 수 있다.
     * <p>
     * 카드 제목(name)은 journal.title을 쓴다. 공개 코스만 조회하므로 null 걱정이 없다.
     * ⚠️ 검색 매칭(keyword)은 여전히 c.name을 대상으로 한다 — design-decisions.md "코스 검색" 확정 사항
     * (검색 대상: course.name + station.station_name)과 정면으로 얽혀 있어 이번 변경에서는 건드리지
     * 않았다. 화면에 보이는 제목(journal.title)과 실제 검색 매칭 대상(course.name)이 달라지므로,
     * "카드에 보이는 글자로 검색해도 안 걸리는" 케이스가 생길 수 있다 — 검색 대상 자체를 바꿀지는
     * 별도 확인 필요.
     */
    @Query("SELECT c.id AS courseId, c.journalId AS journalId, j.title AS name, " +
            "j.createdAt AS createdAt, c.viewCount AS viewCount, c.likeCount AS likeCount, " +
            "s.id AS stationId, s.stationName AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Station s ON s.id = c.stationId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "LEFT JOIN s.drawLine l " +
            "WHERE j.isPublic = true AND " + NOT_WITHDRAWN + " " +
            "AND (:lineId IS NULL OR l.id = :lineId) " +
            "AND (:stationId IS NULL OR s.id = :stationId) " +
            "AND (:conceptTourId IS NULL OR c.conceptTourId = :conceptTourId) " +
            "AND (:keyword IS NULL OR c.name LIKE CONCAT('%', :keyword, '%') ESCAPE '!' " +
            "     OR TRIM(TRAILING '역' FROM s.stationName) LIKE CONCAT('%', :keyword, '%') ESCAPE '!') " +
                        "AND (:createdAt IS NULL OR j.createdAt < :createdAt " +
            "     OR (j.createdAt = :createdAt AND c.id < :courseId)) " +
            "ORDER BY j.createdAt DESC, c.id DESC")
    List<ExploreCourseView> findExploreCoursesByLatest(@Param("lineId") Long lineId,
                                                       @Param("stationId") Long stationId,
                                                       @Param("keyword") String keyword,
                                                       @Param("conceptTourId") Long conceptTourId,
                                                       @Param("createdAt") LocalDateTime createdAt,
                                                       @Param("courseId") Long courseId,
                                                       Pageable pageable);

    /**
     * 둘러보기 코스 목록 - 인기순(view_count + like_count × 2), 동률이면 최신순.
     * <p>
     * 조건은 최신순과 같고 정렬과 커서만 다르다. 커서는 점수를 먼저 비교하고,
     * 점수가 같으면 최신순과 같은 방식으로 시각·id를 비교한다.
     * <p>
     * 점수는 조회수·좋아요가 바뀌면 함께 변한다. 페이징 도중 순위가 흔들려 같은 코스가
     * 두 번 나오거나 빠질 수 있지만, 목록이 실시간으로 요동치는 화면이 아니라 감수한다.
     * <p>
     * 카드 제목(name)은 최신순과 같은 이유로 journal.title을 쓴다. 검색 매칭이
     * 여전히 c.name인 것도 최신순과 동일 — 위 findExploreCoursesByLatest 주석 참고.
     * <p>
     * 동률 tie-break의 createdAt도 최신순과 같은 이유로 j.createdAt(여행일지 작성 시점)이다.
     */
    @Query("SELECT c.id AS courseId, c.journalId AS journalId, j.title AS name, " +
            "j.createdAt AS createdAt, c.viewCount AS viewCount, c.likeCount AS likeCount, " +
            "s.id AS stationId, s.stationName AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Station s ON s.id = c.stationId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "LEFT JOIN s.drawLine l " +
            "WHERE j.isPublic = true AND " + NOT_WITHDRAWN + " " +
            "AND (:lineId IS NULL OR l.id = :lineId) " +
            "AND (:stationId IS NULL OR s.id = :stationId) " +
            "AND (:conceptTourId IS NULL OR c.conceptTourId = :conceptTourId) " +
            "AND (:keyword IS NULL OR c.name LIKE CONCAT('%', :keyword, '%') ESCAPE '!' " +
            "     OR TRIM(TRAILING '역' FROM s.stationName) LIKE CONCAT('%', :keyword, '%') ESCAPE '!') " +
                        "AND (:score IS NULL OR (c.viewCount + c.likeCount * 2) < :score " +
            "     OR ((c.viewCount + c.likeCount * 2) = :score " +
            "         AND (j.createdAt < :createdAt OR (j.createdAt = :createdAt AND c.id < :courseId)))) " +
            "ORDER BY (c.viewCount + c.likeCount * 2) DESC, j.createdAt DESC, c.id DESC")
    List<ExploreCourseView> findExploreCoursesByPopular(@Param("lineId") Long lineId,
                                                        @Param("stationId") Long stationId,
                                                        @Param("keyword") String keyword,
                                                        @Param("conceptTourId") Long conceptTourId,
                                                        @Param("score") Long score,
                                                        @Param("createdAt") LocalDateTime createdAt,
                                                        @Param("courseId") Long courseId,
                                                        Pageable pageable);

    /**
     * 사람들이 많이 찾는 코스 - 좋아요 수 내림차순, 동률이면 조회수 내림차순, 그마저 동률이면 최신순.
     * <p>
     * 둘러보기 목록의 "인기순"(조회수 + 좋아요 × 2)과는 다른 기준이다. 화면 부제가
     * "가장 많이 담아둔 코스"라 담은 횟수, 즉 좋아요 수를 1차로 본다.
     * 조회수를 2차 정렬로 반영한다 — 좋아요 수가 같으면 더 많이 본(검증된) 코스를 우선한다.
     * <p>
     * 최신순 tie-break는 c.createdAt(코스 저장 시점)이 아니라 j.createdAt(여행일지 작성 시점)이다 —
     * 코스는 일지 없이 먼저 생성될 수 있어 코스 저장 시점 기준이면 실제로는 한참 뒤에
     * 일지를 쓴 코스가 방금 저장만 해둔 코스보다 밀릴 수 있다.
     * <p>
     * 상위 몇 개까지 보여줄지는 서비스가 정한다. 이 쿼리는 정렬만 책임진다.
     * <p>
     * 카드 제목(name)은 위 두 목록과 같은 이유로 journal.title을 쓴다.
     */
    @Query("SELECT c.id AS courseId, c.journalId AS journalId, j.title AS name, " +
            "j.createdAt AS createdAt, c.viewCount AS viewCount, c.likeCount AS likeCount, " +
            "s.id AS stationId, s.stationName AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Station s ON s.id = c.stationId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "LEFT JOIN s.drawLine l " +
            "WHERE j.isPublic = true AND " + NOT_WITHDRAWN + " " +
            "ORDER BY c.likeCount DESC, c.viewCount DESC, j.createdAt DESC, c.id DESC")
    List<ExploreCourseView> findMostLikedCourses(Pageable pageable);

    /**
     * 노선 칩으로 그릴 후보 노선. 뽑기 역이 속한 노선 중 {@code lineCodes}에 해당하는 것만 내려준다.
     * <p>
     * 코스는 뽑기 역에만 붙으므로 이 집합이 곧 "코스가 존재할 수 있는 노선"이다.
     * {@code line} 테이블 전체를 쓰면 코스가 생길 수 없는 노선까지 칩으로 뜨고,
     * 공개 코스가 있는 노선만 쓰면 데이터가 쌓일 때마다 칩이 늘어나 노선도가 흔들려 보인다.
     * <p>
     * 이 쿼리만 소속 호선({@code StationLine}) 기준이다.
     * 화면에 칩이 있는 노선만 남기는 것은 호출부가 {@code lineCodes}로 정한다.
     * <p>
     * 뽑기 역이 늘어나면 노선도 자연히 늘어나므로 목록을 하드코딩하지 않고 데이터에서 유도한다.
     */
    @Query("SELECT DISTINCT l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Station s " +
            "JOIN StationLine sl ON sl.station.id = s.id " +
            "JOIN sl.line l " +
            "WHERE s.isDrawable = true AND l.code IN :lineCodes " +
            "ORDER BY l.name")
    List<LineView> findDrawableLines(@Param("lineCodes") Collection<LineCode> lineCodes);

    /**
     * 공개 코스가 하나라도 있는 노선. 칩의 활성/비활성을 가르는 데 쓴다.
     * <p>
     * 노선 필터와 같은 기준(대표 호선)으로 조회해야 한다. 기준이 다르면
     * "칩은 활성인데 필터를 걸면 결과가 없는" 불일치가 생긴다.
     * <p>
     * 칩으로 그리지 않는 노선까지 섞여 나오지만, 활성 여부를 가리는 데만 쓰는 값이라
     * 칩에 없는 노선의 id는 조회되지 않아 결과가 달라지지 않는다.
     */
    @Query("SELECT DISTINCT l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM Course c " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Station s ON s.id = c.stationId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "JOIN s.drawLine l " +
            "WHERE j.isPublic = true AND " + NOT_WITHDRAWN + " " +
            "ORDER BY l.name")
    List<LineView> findLinesWithPublicCourses();

    /**
     * "역 선택" 드롭다운으로 그릴 후보 역. 뽑기 역만 내려준다.
     * <p>
     * 코스는 뽑기 역에만 붙으므로 이 집합이 곧 "코스가 존재할 수 있는 역"이다.
     * 노선 칩과 같은 이유로, 공개 코스가 있는 역만 내려주면 데이터가 쌓일 때마다 항목이 늘어난다.
     * <p>
     * 노선 칩으로 좁힌 범위에서 고르는 목록이라 {@code lineId}만 반영하고, 역·검색어 필터는 반영하지 않는다.
     * 지금 고른 역으로 좁히면 드롭다운에 그 역 하나만 남아 다른 역으로 바꿀 수 없다.
     * <p>
     * 노선 필터와 같은 기준(대표 호선)으로 걸러야 한다. 소속 호선 전체로 걸렀다면
     * 드롭다운엔 뜨지만 고르면(노선 필터와 함께) 결과가 없는 역이 섞여 나올 수 있다.
     */
    @Query("SELECT s.id AS stationId, s.stationName AS stationName " +
            "FROM Station s " +
            "WHERE s.isDrawable = true " +
            "AND (:lineId IS NULL OR s.drawLine.id = :lineId) " +
            "ORDER BY s.stationName")
    List<StationView> findDrawableStations(@Param("lineId") Long lineId);

    /**
     * 공개 코스가 하나라도 있는 역. "역 선택" 항목의 활성/비활성을 가르는 데 쓴다.
     * <p>
     * 노선 필터와 같은 기준(대표 호선)으로 조회해야 한다. 기준이 다르면
     * "목록에서 고를 수 있는 역인데 고르면 결과가 없는" 불일치가 생긴다.
     */
    @Query("SELECT DISTINCT s.id AS stationId, s.stationName AS stationName " +
            "FROM Course c " +
            "JOIN Journal j ON j.id = c.journalId " +
            "JOIN Station s ON s.id = c.stationId " +
            "JOIN Member mem ON mem.id = c.memberId " +
            "WHERE j.isPublic = true AND " + NOT_WITHDRAWN + " " +
            "AND (:lineId IS NULL OR s.drawLine.id = :lineId) " +
            "ORDER BY s.stationName")
    List<StationView> findStationsWithPublicCourses(@Param("lineId") Long lineId);

    interface PopularCourseView {
        Long getCourseId();
        String getName();
        int getViewCount();
        int getLikeCount();
    }

    interface ExploreCourseView {
        Long getCourseId();
        Long getJournalId();
        String getName();
        LocalDateTime getCreatedAt();
        int getViewCount();
        int getLikeCount();
        Long getStationId();
        String getStationName();
        Long getLineId();
        String getLineName();
        LineCode getLineCode();
    }

    interface CourseDetailView {
        Long getCourseId();
        String getName();
        // findMyCourseDetail만 이 필드를 채운다. 다른 쿼리(findPublicCourseDetail/findShareCourseDetail)를
        // 쓰는 컨버터는 getShareToken()을 호출하지 않으므로 비어 있어도 문제없다.
        String getShareToken();
        Long getStationId();
        String getStationName();
        Long getLineId();
        String getLineName();
        LineCode getLineCode();
    }

    interface MyCourseView {
        Long getCourseId();
        String getName();
        LocalDateTime getCreatedAt();
        Long getStationId();
        String getStationName();
        Long getLineId();
        String getLineName();
        LineCode getLineCode();
    }

    interface MemberCourseCardView {
        Long getCourseId();
        Long getJournalId();
        String getName();
        LocalDateTime getCreatedAt();
        int getLikeCount();
        Long getStationId();
        String getStationName();
        Long getLineId();
        String getLineName();
        LineCode getLineCode();
    }

    interface PlaceCourseView {
        Long getCourseId();
        Long getJournalId();
        String getName();
        Long getStationId();
        String getStationName();
        Long getLineId();
        String getLineName();
        LineCode getLineCode();
    }

    interface LineView {
        Long getLineId();
        String getLineName();
        LineCode getLineCode();
    }

    interface StationView {
        Long getStationId();
        String getStationName();
    }

    // 리포트용 코스 저장 수
    @Query("SELECT COUNT(c) FROM Course c "
            + "WHERE c.originalCourseId IS NULL AND c.createdAt >= :from AND c.createdAt < :to")
    long countCreatedInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
