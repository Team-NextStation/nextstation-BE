package com.cotato.nextstation.domain.auth.client;

import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// JWKS 네트워크 호출은 이 스위트에서 검증하지 않는다 - 부팅 시점 방어와, 네트워크를 타기 전에
// 파싱 단계에서 걸러지는(위변조가 명백한) 토큰만 확인한다.
class AppleOAuthClientTest {

    private AppleOAuthClient client() {
        return new AppleOAuthClient(List.of("com.cotato.nextstation"));
    }

    @Test
    @DisplayName("allowed-audiences 설정이 비어 있으면 생성 시점에 실패한다")
    void constructor_emptyAllowedAudiences() {
        assertThatThrownBy(() -> new AppleOAuthClient(List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("JWT 형식이 아닌 문자열이면 JWKS 조회 없이 예외가 발생한다")
    void verify_malformedToken() {
        assertThatThrownBy(() -> client().verify("not-a-jwt"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN.getMessage());
    }

    @Test
    @DisplayName("서명이 없는 alg=none 토큰이면 예외가 발생한다")
    void verify_unsignedToken() {
        // header: {"alg":"none"}, payload: {"sub":"1"} - 서명부 없이 점(.)만 붙인 형태
        String header = base64UrlEncode("{\"alg\":\"none\"}");
        String payload = base64UrlEncode("{\"sub\":\"1\"}");
        String unsignedToken = header + "." + payload + ".";

        assertThatThrownBy(() -> client().verify(unsignedToken))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN.getMessage());
    }

    private String base64UrlEncode(String json) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
