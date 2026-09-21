package com.cotato.nextstation.domain.auth.client.dto;

import io.jsonwebtoken.Claims;

// AppleOAuthClient.verify()가 서명/클레임 검증까지 마친 identity token에서 우리가 쓰는 값만 추린 DTO.
// 카카오의 KakaoUserInfoResponse(REST 응답)에 대응하지만, 얘는 REST 응답이 아니라 JWT claims에서 뽑는다.
public record AppleIdentityToken(

        // Apple이 앱(Bundle ID)별로 발급하는 고유/영구 고정값 -> MemberSocialAccount.providerUserId로 저장
        String providerUserId,

        // Apple은 email_verified가 true일 때만 이메일을 신뢰한다.
        // Apple 자체가 이메일 소유권을 보증하는 계정 시스템이라 카카오보다는 신뢰도가 높지만, 방어적으로 확인한다.
        String email
) {

    public static AppleIdentityToken from(Claims claims) {
        String providerUserId = claims.getSubject();
        String email = isEmailVerified(claims) ? claims.get("email", String.class) : null;
        return new AppleIdentityToken(providerUserId, email);
    }

    // Apple은 이 클레임을 boolean 또는 문자열("true"/"false")로 내려줄 수 있어 타입을 가리지 않고 비교한다.
    private static boolean isEmailVerified(Claims claims) {
        Object emailVerified = claims.get("email_verified");
        return "true".equalsIgnoreCase(String.valueOf(emailVerified));
    }
}
