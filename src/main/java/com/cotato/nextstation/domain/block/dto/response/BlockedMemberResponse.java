package com.cotato.nextstation.domain.block.dto.response;

import com.cotato.nextstation.domain.block.repository.MemberBlockRepository.BlockedMemberView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "차단한 사용자 정보")
public record BlockedMemberResponse(

        @Schema(description = "차단된 회원 id", example = "1")
        Long memberId,

        @Schema(description = "차단된 회원 닉네임", example = "여행자")
        String nickname,

        @Schema(description = "차단된 회원 프로필 이미지 URL")
        String profileImageUrl,

        @Schema(description = "차단한 시점")
        LocalDateTime blockedAt
) {
    public static BlockedMemberResponse from(BlockedMemberView view) {
        return new BlockedMemberResponse(
                view.getMemberId(),
                view.getNickname(),
                view.getProfileImageUrl(),
                view.getBlockedAt()
        );
    }
}
