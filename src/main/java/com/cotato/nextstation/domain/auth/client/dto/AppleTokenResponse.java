package com.cotato.nextstation.domain.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// POST https://appleid.apple.com/auth/token 응답 매핑용 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppleTokenResponse(

        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,

        // 이 값을 SocialOauthCredential에 암호화해서 저장해뒀다가, 탈퇴 시 revoke에 사용한다.
        @JsonProperty("refresh_token") String refreshToken,

        // identityToken과 동일한 값이라 별도로 검증/저장하지 않는다.
        @JsonProperty("id_token") String idToken
) {
}
