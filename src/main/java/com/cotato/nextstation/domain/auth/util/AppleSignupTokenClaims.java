package com.cotato.nextstation.domain.auth.util;

// appleSignupToken 발급(AppleLoginQueryService)과 검증(AppleSignupCommandService)이 공유하는 claim 상수
// 주의: subject는 memberId가 아니라 Apple 회원번호(providerUserId)다 -> 다른 토큰과 다름
public final class AppleSignupTokenClaims {

    public static final String PURPOSE_KEY = "purpose";
    public static final String APPLE_SIGNUP_PURPOSE = "APPLE_SIGNUP";

    public static final String EMAIL_KEY = "email";

    private AppleSignupTokenClaims() {
    }
}
