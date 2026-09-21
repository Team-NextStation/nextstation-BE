package com.cotato.nextstation.domain.auth.service.result;

import com.cotato.nextstation.domain.member.entity.MemberStatus;

/**
 * ProfileSetupCommandService -> AuthController 전달 전용
 * accessToken은 응답 body로, refreshToken은 쿠키로 내려가므로 분리한다.
 */
public record ProfileSetupResult(Long memberId, String nickname, MemberStatus status,
                                 String accessToken, String refreshToken) {
}
