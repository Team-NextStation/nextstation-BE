package com.cotato.nextstation.domain.auth.dto.response;

import com.cotato.nextstation.domain.member.entity.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로필 설정 응답")
public record ProfileSetupResponse(

        @Schema(description = "회원 id", example = "1")
        Long memberId,

        @Schema(description = "설정된 닉네임", example = "환승러")
        String nickname,

        @Schema(description = "프로필 설정 완료 후 회원 상태", example = "ACTIVE")
        MemberStatus status,

        @Schema(description = "API 요청 시 Authorization: Bearer 헤더에 담아 보내는 access token. 1시간 후 만료된다.", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken
) {
}