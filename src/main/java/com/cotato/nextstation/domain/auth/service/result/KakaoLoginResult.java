package com.cotato.nextstation.domain.auth.service.result;

import com.cotato.nextstation.domain.member.entity.MemberRole;

// KakaoLoginQueryService -> AuthController 전달 전용, resultType에 따라 유효한 필드가 다르다.
public record KakaoLoginResult(
        KakaoLoginResultType resultType,
        Long memberId,
        String accessToken,
        String refreshToken,
        String signupToken,
        String kakaoSignupToken,
        String kakaoNickname,
        String kakaoProfileImageUrl,
        boolean restored,
        MemberRole role
) {
}
