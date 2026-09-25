package com.cotato.nextstation.domain.block.repository;

import com.cotato.nextstation.domain.block.entity.MemberBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

import static com.cotato.nextstation.domain.member.repository.MemberRepository.NOT_WITHDRAWN;

public interface MemberBlockRepository extends JpaRepository<MemberBlock, Long> {

    // 여러 작성자가 섞인 목록(장소 리뷰 등)에서, 조회자(:currentMemberId)와 작성자(mem) 사이에 어느
    // 방향으로든 차단 관계가 있으면 그 행을 제외하는 조건. 양방향이라 서로의 컨텐츠가 서로에게 안 보인다.
    // 비로그인(:currentMemberId가 null)이면 아무도 걸러지지 않는다. NOT_WITHDRAWN과 마찬가지로 별칭 "mem"을 요구한다.
    String NOT_BLOCKED_BY_VIEWER = "(:currentMemberId IS NULL OR (" +
            "mem.id NOT IN (SELECT mb.blockedId FROM MemberBlock mb WHERE mb.blockerId = :currentMemberId) " +
            "AND mem.id NOT IN (SELECT mb.blockerId FROM MemberBlock mb WHERE mb.blockedId = :currentMemberId)))";

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 두 회원 사이에 어느 방향으로든 차단 관계가 있는지. 프로필/스탬프/코스/일지처럼 단일 대상을
    // 조회할 때, 내가 상대를 차단했거나 상대가 나를 차단했으면 서로에게 컨텐츠가 안 보이게 한다.
    @Query("SELECT COUNT(mb) > 0 FROM MemberBlock mb " +
            "WHERE (mb.blockerId = :a AND mb.blockedId = :b) OR (mb.blockerId = :b AND mb.blockedId = :a)")
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b);

    List<MemberBlock> findByBlockerId(Long blockerId);

    long deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 최종 파기(hard delete) 시점 정리용. 탈퇴 즉시가 아니라 유예 기간이 끝나 실제로
    // 파기될 때만 지운다 - 유예 기간 중엔 NOT_WITHDRAWN이 콘텐츠를 이미 가려주므로 차단
    // 행이 남아있어도 영향이 없고, 덕분에 복구 시 되돌리는 로직 없이도 차단이 그대로 유지된다.
    void deleteByBlockerIdOrBlockedId(Long blockerId, Long blockedId);

    // 차단 목록 조회. 닉네임/프로필이미지를 화면에 바로 그릴 수 있도록 Member를 조인해서 가져온다.
    // 탈퇴 즉시 차단 행을 지우지 않으므로, 유예 기간 중인 탈퇴 회원이 목록에 남아있지 않도록
    // NOT_WITHDRAWN을 건다.
    @Query("SELECT mem.id AS memberId, mem.nickname AS nickname, mem.profileImageUrl AS profileImageUrl, " +
            "mb.createdAt AS blockedAt " +
            "FROM MemberBlock mb " +
            "JOIN Member mem ON mem.id = mb.blockedId " +
            "WHERE mb.blockerId = :blockerId AND " + NOT_WITHDRAWN + " " +
            "ORDER BY mb.createdAt DESC")
    List<BlockedMemberView> findBlockedMembers(@Param("blockerId") Long blockerId);

    interface BlockedMemberView {
        Long getMemberId();
        String getNickname();
        String getProfileImageUrl();
        LocalDateTime getBlockedAt();
    }
}
