package com.cotato.nextstation.domain.journal.repository;

import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.station.entity.LineCode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface JournalRepository extends JpaRepository<Journal, Long> {

    boolean existsByIdAndMember_Id(Long journalId, Long memberId);
    boolean existsByMemberStampId(Long memberStampId);

    // 이미 여행일지가 작성된 memberStampId 목록 조회
    @Query("SELECT j.memberStampId FROM Journal j WHERE j.member.id = :memberId")
    Set<Long> findCompletedMemberStampIdsByMemberId(@Param("memberId") Long memberId);

    // 내 여행일지 목록 (최신순), 첫 페이지. 카드에 필요한 코스 좋아요 수·역·대표 호선까지 한 번에
    // 가져온다(일지마다 조회하면 N+1). Journal은 memberStampId만 들고 있어(연관관계 미매핑)
    // MemberStamp·Course·Station을 id로 ad-hoc 조인한다.
    // Course·Journal 모두 @SQLRestriction(is_deleted = false)이 걸려 있어 JPQL로 조인하면
    // 완주 후 코스가 삭제된 일지가 통째로 사라진다. 네이티브 쿼리로 그 제약을 우회해
    // 삭제된 코스라도 삭제 시점의 좋아요 수·역 정보를 그대로 보존해서 보여주고,
    // Journal 쪽 is_deleted 필터만 직접 명시한다.
    @Query(value = "SELECT j.id AS journalId, j.title AS title, j.created_at AS createdAt, " +
            "c.like_count AS likeCount, s.station_name AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM journal j " +
            "JOIN member_place_stamps ms ON ms.id = j.member_stamp_id " +
            "JOIN course c ON c.id = ms.course_id " +
            "JOIN station s ON s.id = c.station_id " +
            "LEFT JOIN line l ON l.id = s.draw_line_id " +
            "WHERE j.member_id = :memberId AND j.is_deleted = false " +
            "ORDER BY j.created_at DESC, j.id DESC",
            nativeQuery = true)
    List<MyJournalCardView> findMyJournalCards(@Param("memberId") Long memberId, Pageable pageable);

    // 다음 페이지. 생성 시각이 같을 수 있어 id를 tie-breaker로 함께 비교한다.
    @Query(value = "SELECT j.id AS journalId, j.title AS title, j.created_at AS createdAt, " +
            "c.like_count AS likeCount, s.station_name AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM journal j " +
            "JOIN member_place_stamps ms ON ms.id = j.member_stamp_id " +
            "JOIN course c ON c.id = ms.course_id " +
            "JOIN station s ON s.id = c.station_id " +
            "LEFT JOIN line l ON l.id = s.draw_line_id " +
            "WHERE j.member_id = :memberId AND j.is_deleted = false " +
            "AND (j.created_at < :createdAt OR (j.created_at = :createdAt AND j.id < :journalId)) " +
            "ORDER BY j.created_at DESC, j.id DESC",
            nativeQuery = true)
    List<MyJournalCardView> findMyJournalCardsAfterCursor(@Param("memberId") Long memberId,
                                                          @Param("createdAt") LocalDateTime createdAt,
                                                          @Param("journalId") Long journalId,
                                                          Pageable pageable);

    // 코스 정보 스냅샷 (삭제된 코스 포함). 여행일지 작성 초기 정보/상세 조회는 완주 후 코스가
    // 삭제돼도 그 코스 이름 등을 그대로 보여줘야 한다. Course의 @SQLRestriction(is_deleted = false) 때문에
    // 일반 조회(findById)는 삭제된 코스를 COURSE_NOT_FOUND로 취급하므로, 여기서는 course 테이블을
    // 직접 네이티브로 조회해 그 제약을 우회한다. soft delete는 is_deleted만 켜고 다른 컬럼은
    // 그대로 남기므로 삭제된 코스도 완주 당시 값을 그대로 돌려줄 수 있다.
    @Query(value = "SELECT c.id AS courseId, c.name AS name, c.station_id AS stationId, " +
            "c.view_count AS viewCount, c.like_count AS likeCount " +
            "FROM course c WHERE c.id = :courseId",
            nativeQuery = true)
    Optional<CourseSnapshotView> findCourseSnapshotById(@Param("courseId") Long courseId);

    // 여행일지 미작성 목록 카드용 코스 정보 배치 조회 (삭제된 코스 포함). course→station→line을
    // 네이티브로 직접 조인해 코스 이름·역 이름·대표 호선을 한 번에 가져온다
    // (findMyJournalCards와 같은 기법). 완주 당시 스탬프라 코스가 이후 삭제됐을 수 있고,
    // 스탬프 개수만큼 개별 조회하면 코스 하나만 삭제돼도(또는 그냥 N+1로도) 전체가 영향받으므로
    // 삭제 여부와 무관하게 한 번에 가져온다.
    @Query(value = "SELECT c.id AS courseId, c.name AS name, s.station_name AS stationName, " +
            "l.id AS lineId, l.name AS lineName, l.code AS lineCode " +
            "FROM course c " +
            "JOIN station s ON s.id = c.station_id " +
            "LEFT JOIN line l ON l.id = s.draw_line_id " +
            "WHERE c.id IN :courseIds",
            nativeQuery = true)
    List<UncompletedCourseCardView> findUncompletedCourseCardsByIds(@Param("courseIds") Collection<Long> courseIds);

    interface CourseSnapshotView {
        Long getCourseId();
        String getName();
        Long getStationId();
        int getViewCount();
        int getLikeCount();
    }

    interface UncompletedCourseCardView {
        Long getCourseId();
        String getName();
        String getStationName();
        Long getLineId();
        String getLineName();
        LineCode getLineCode();
    }

    interface MyJournalCardView {
        Long getJournalId();
        String getTitle();
        LocalDateTime getCreatedAt();
        int getLikeCount();
        String getStationName();
        Long getLineId();
        String getLineName();
        LineCode getLineCode();
    }

    // 리포트용 여행일지 작성 수
    @Query("SELECT COUNT(j) FROM Journal j WHERE j.createdAt >= :from AND j.createdAt < :to")
    long countCreatedInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}