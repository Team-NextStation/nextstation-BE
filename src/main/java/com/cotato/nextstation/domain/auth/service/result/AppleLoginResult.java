package com.cotato.nextstation.domain.auth.service.result;

import com.cotato.nextstation.domain.member.entity.MemberRole;

// AppleLoginQueryService -> AuthController 전달 전용, resultType에 따라 유효한 필드가 다르다.
public record AppleLoginResult(
        AppleLoginResultType resultType,
        Long memberId,
        String accessToken,
        String refreshToken,
        String signupToken,
        String appleSignupToken,
        boolean restored,
        // 프론트가 관리자 메뉴 노출 여부를 판단하는 데 사용 -> LOGIN_SUCCESS일 때만 값이 있다.
        MemberRole role
) {
}
