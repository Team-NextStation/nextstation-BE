package com.cotato.nextstation.domain.block.service.query;

import com.cotato.nextstation.domain.block.dto.response.BlockedMemberListResponse;
import com.cotato.nextstation.domain.block.dto.response.BlockedMemberResponse;
import com.cotato.nextstation.domain.block.repository.MemberBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberBlockQueryService {

    private final MemberBlockRepository memberBlockRepository;

    public BlockedMemberListResponse getBlockedMembers(Long blockerId) {
        return new BlockedMemberListResponse(
                memberBlockRepository.findBlockedMembers(blockerId).stream()
                        .map(BlockedMemberResponse::from)
                        .toList()
        );
    }
}
