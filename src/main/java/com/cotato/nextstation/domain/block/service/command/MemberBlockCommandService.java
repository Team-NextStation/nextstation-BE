package com.cotato.nextstation.domain.block.service.command;

import com.cotato.nextstation.domain.block.entity.MemberBlock;
import com.cotato.nextstation.domain.block.exception.MemberBlockErrorCode;
import com.cotato.nextstation.domain.block.repository.MemberBlockRepository;
import com.cotato.nextstation.global.exception.CustomException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class MemberBlockCommandService {

    public MemberBlockRepository memberBlockRepository;

    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new CustomException(MemberBlockErrorCode.SELF_BLOCK_NOT_ALLOWED);
        }
        if (memberBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new CustomException(MemberBlockErrorCode.ALREADY_BLOCKED);
        }
        try {
            memberBlockRepository.save(MemberBlock.builder()
                    .blockerId(blockerId)
                    .blockedId(blockedId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(MemberBlockErrorCode.ALREADY_BLOCKED);
        }
    }

    public void unblock(Long blockerId, Long blockedId) {
        long deleted = memberBlockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
        if (deleted == 0) {
            throw new CustomException(MemberBlockErrorCode.BLOCK_NOT_FOUND);
        }
    }
}
