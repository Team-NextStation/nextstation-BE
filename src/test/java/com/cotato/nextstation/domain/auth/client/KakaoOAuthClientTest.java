package com.cotato.nextstation.domain.auth.client;

import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 허용목록 검증은 인가코드 유출과 직결되므로, 카카오로 요청이 나가기 전에 막히는 경로만 검증한다.
class KakaoOAuthClientTest {

    private static final String ADMIN_KEY = "admin-key";
    private static final String ALLOWED_URI = "https://app.example.com/auth/kakao/callback";

    private KakaoOAuthClient client() {
        return new KakaoOAuthClient("client-id", "", List.of(ALLOWED_URI), ADMIN_KEY);
    }

    @Test
    @DisplayName("허용목록에 없는 redirectUri면 카카오 호출 전에 예외가 발생한다")
    void exchangeToken_unregisteredRedirectUri() {
        assertThatThrownBy(() -> client().exchangeToken("code", "https://evil.example.org/auth/kakao/callback"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.UNREGISTERED_REDIRECT_URI.getMessage());
    }

    @Test
    @DisplayName("허용 URI로 시작하기만 하는 외부 도메인은 통과시키지 않는다")
    void exchangeToken_prefixMatchIsNotAllowed() {
        assertThatThrownBy(() -> client().exchangeToken("code", ALLOWED_URI + ".evil.example.org"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.UNREGISTERED_REDIRECT_URI.getMessage());
    }

    @Test
    @DisplayName("redirect-uris 설정이 비어 있으면 생성 시점에 실패한다")
    void constructor_emptyRedirectUris() {
        assertThatThrownBy(() -> new KakaoOAuthClient("client-id", "", List.of(), ADMIN_KEY))
                .isInstanceOf(IllegalStateException.class);
    }
}