package com.cotato.nextstation.domain.auth.service.result;

import com.cotato.nextstation.domain.member.entity.MemberRole;

/**
 * AuthTokenService -> AuthController 전달 전용
 * rotation으로 refreshToken도 매번 새로 발급되므로 accessToken과 함께 담아 내려보낸다.
 */
public record ReissueResult(Long memberId, String accessToken, String refreshToken, MemberRole role) {
}
