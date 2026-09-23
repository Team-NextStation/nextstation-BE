package com.cotato.nextstation.domain.block.repository;

import com.cotato.nextstation.domain.block.entity.MemberBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberBlockRepository extends JpaRepository<MemberBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<MemberBlock> findByBlockerId(Long blockerId);

    long deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 탈퇴 시 자동 해제용
    void deleteByBlockerIdOrBlockedId(Long blockerId, Long blockedId);
}
