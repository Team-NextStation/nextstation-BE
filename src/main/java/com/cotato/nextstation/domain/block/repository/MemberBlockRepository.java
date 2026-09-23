package com.cotato.nextstation.domain.block.repository;

import com.cotato.nextstation.domain.block.entity.MemberBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MemberBlockRepository extends JpaRepository<MemberBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<MemberBlock> findByBlockerId(Long blockerId);

    long deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 탈퇴 시 자동 해제용
    void deleteByBlockerIdOrBlockedId(Long blockerId, Long blockedId);

    // 차단 목록 조회. 닉네임/프로필이미지를 화면에 바로 그릴 수 있도록 Member를 조인해서 가져온다.
    @Query("SELECT mem.id AS memberId, mem.nickname AS nickname, mem.profileImageUrl AS profileImageUrl, " +
            "mb.createdAt AS blockedAt " +
            "FROM MemberBlock mb " +
            "JOIN Member mem ON mem.id = mb.blockedId " +
            "WHERE mb.blockerId = :blockerId " +
            "ORDER BY mb.createdAt DESC")
    List<BlockedMemberView> findBlockedMembers(@Param("blockerId") Long blockerId);

    interface BlockedMemberView {
        Long getMemberId();
        String getNickname();
        String getProfileImageUrl();
        LocalDateTime getBlockedAt();
    }
}
