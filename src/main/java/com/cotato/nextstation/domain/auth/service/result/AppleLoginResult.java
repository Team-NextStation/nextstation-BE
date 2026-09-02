package com.cotato.nextstation.domain.auth.service.result;

// AppleLoginQueryService -> AuthController 전달 전용, resultType에 따라 유효한 필드가 다르다.
public record AppleLoginResult(
        AppleLoginResultType resultType,
        Long memberId,
        String accessToken,
        String refreshToken,
        String signupToken,
        String appleSignupToken,
        boolean restored
) {
}
