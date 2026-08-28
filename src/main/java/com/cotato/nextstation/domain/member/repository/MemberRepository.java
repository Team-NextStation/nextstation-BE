package com.cotato.nextstation.domain.member.repository;

import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * 탈퇴(WITHDRAWN)한 회원의 콘텐츠를 다른 회원에게 노출하지 않기 위한 조건절. 여러 도메인의
     * 조회 쿼리가 이 조건을 그대로 재사용한다 (PlaceReviewRepository, CourseRepository,
     * MemberStampRepository, CourseLikeRepository 등).
     * <p>
     * 별칭은 반드시 "mem"으로 맞춰야 한다. Member를 memberId만으로 참조하는 엔티티는
     * {@code JOIN Member mem ON mem.id = ...memberId}로 ad-hoc 조인하고, 실제 연관관계로
     * 참조하는 엔티티(PlaceReview → Journal → Member)는 {@code JOIN FETCH j.member mem}처럼
     * 연관관계 조인에 이 별칭을 붙여서 쓴다.
     */
    String NOT_WITHDRAWN = "mem.status <> com.cotato.nextstation.domain.member.entity.MemberStatus.WITHDRAWN";

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    // 유예가 끝난 탈퇴 회원 (hard delete 배치 대상). 삭제 SQL이 native라 엔티티 대신 ID만 가져온다.
    @Query("SELECT m.id FROM Member m WHERE m.status = :status AND m.deletedAt < :threshold")
    List<Long> findIdsByStatusAndDeletedAtBefore(MemberStatus status, LocalDateTime threshold);

    /**
     * 프로필 설정 완료 상태 전환을 선점한다. 갱신된 행 수를 반환하며, 0이면 이미 다른 요청이 전환을 끝낸 것이다.
     * 조회 시점의 status 검사만으로는 같은 signupToken으로 동시에 들어온 두 요청이 모두 통과해
     * 프로필이 덮어써지고 로그인 세션도 중복 발급되므로, 전환 자체를 조건부 갱신으로 원자화한다.
     */
    @Modifying
    @Query("UPDATE Member m SET m.status = com.cotato.nextstation.domain.member.entity.MemberStatus.ACTIVE "
            + "WHERE m.id = :memberId AND m.status = com.cotato.nextstation.domain.member.entity.MemberStatus.PENDING")
    int activateIfPending(@Param("memberId") Long memberId);

    // 프로필 설정 전 PENDING 상태도 가입으로 카운트
    @Query("SELECT COUNT(m) FROM Member m WHERE m.createdAt >= :from AND m.createdAt < :to")
    long countJoinedInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    long countByStatusNot(MemberStatus status);
    /**
     * 탈퇴 상태 전환을 선점한다. 갱신된 행 수를 반환하며, 0이면 이미 다른 요청이 탈퇴를 끝낸 것이다.
     * 조회 시점의 status 검사만으로는 동시에 들어온 두 탈퇴 요청이 모두 통과해 코스/리뷰
     * 좋아요 수 감소(decreaseLikeCountForLikesByMember 등)가 이중 실행될 수 있으므로,
     * 전환 자체를 조건부 갱신으로 원자화해 실제로 전환에 성공한 요청만 뒤이은 부수 효과를 처리하게 한다.
     */
    @Modifying
    @Query("UPDATE Member m SET m.status = com.cotato.nextstation.domain.member.entity.MemberStatus.WITHDRAWN, "
            + "m.deletedAt = :deletedAt "
            + "WHERE m.id = :memberId AND m.status <> com.cotato.nextstation.domain.member.entity.MemberStatus.WITHDRAWN")
    int withdrawIfNotAlready(@Param("memberId") Long memberId, @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 유예 기간 내 복구 상태 전환을 선점한다. 갱신된 행 수가 0이면 이미 다른 요청이 복구를
     * 끝낸 것이므로, 좋아요 수 복구(increaseLikeCountForLikesByMember 등)를 다시 실행하지 않는다.
     * targetStatus는 프로필 완료 여부(닉네임 유무)에 따라 호출부가 미리 정한다.
     */
    @Modifying
    @Query("UPDATE Member m SET m.status = :targetStatus, m.deletedAt = null "
            + "WHERE m.id = :memberId AND m.status = com.cotato.nextstation.domain.member.entity.MemberStatus.WITHDRAWN")
    int restoreIfWithdrawn(@Param("memberId") Long memberId, @Param("targetStatus") MemberStatus targetStatus);
}
