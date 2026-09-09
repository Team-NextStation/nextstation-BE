package com.cotato.nextstation.domain.auth.controller;

import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.repository.AppleWebLoginStateRepository;
import com.cotato.nextstation.domain.auth.service.query.AppleLoginQueryService;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResult;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResultType;
import com.cotato.nextstation.domain.auth.util.RefreshTokenCookieFactory;
import com.cotato.nextstation.domain.member.entity.MemberRole;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppleWebAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "apple.oauth.web-client-id=com.cotato.nextstation.web",
        "apple.oauth.web-redirect-uri=https://api.nextstation.app/api/v1/auth/apple/web/callback",
        "apple.oauth.web-frontend-redirect-uri=https://nextstation.app/auth/apple/callback"
})
class AppleWebAuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AppleLoginQueryService appleLoginQueryService;

    @MockitoBean
    AppleWebLoginStateRepository appleWebLoginStateRepository;

    @MockitoBean
    RefreshTokenCookieFactory refreshTokenCookieFactory;

    @MockitoBean
    JwtProvider jwtProvider;

    @Test
    @DisplayName("authorize는 Apple 인증 URL로 302 리다이렉트하고, state에 대응하는 nonce를 저장한다")
    void authorize_redirectsToAppleWithStateAndNonce() throws Exception {
        mockMvc.perform(get("/api/v1/auth/apple/web/authorize"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("https://appleid.apple.com/auth/authorize?"),
                        org.hamcrest.Matchers.containsString("client_id=com.cotato.nextstation.web"),
                        org.hamcrest.Matchers.containsString("response_mode=form_post"),
                        org.hamcrest.Matchers.containsString("state="),
                        org.hamcrest.Matchers.containsString("nonce=")
                )));

        // 원문 nonce는 응답(Apple URL)에 절대 노출되면 안 된다 - 해시값만 노출되고, 원문은 서버(Redis)에만 저장된다.
        then(appleWebLoginStateRepository).should().save(anyString(), anyString());
    }

    @Test
    @DisplayName("콜백이 LOGIN_SUCCESS면 refreshToken 쿠키를 내리고 accessToken을 담아 프론트로 리다이렉트한다")
    void callback_loginSuccess_setsCookieAndRedirects() throws Exception {
        given(appleWebLoginStateRepository.consume("valid-state")).willReturn(Optional.of("raw-nonce"));
        given(appleLoginQueryService.login("id-token", "raw-nonce")).willReturn(
                new AppleLoginResult(AppleLoginResultType.LOGIN_SUCCESS, 1L, "access-token", "refresh-token",
                        null, null, false, MemberRole.USER)
        );
        given(refreshTokenCookieFactory.create("refresh-token"))
                .willReturn(ResponseCookie.from("refreshToken", "refresh-token").httpOnly(true).build());

        mockMvc.perform(post("/api/v1/auth/apple/web/callback")
                        .param("state", "valid-state")
                        .param("id_token", "id-token"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("https://nextstation.app/auth/apple/callback?"),
                        org.hamcrest.Matchers.containsString("resultType=LOGIN_SUCCESS"),
                        org.hamcrest.Matchers.containsString("accessToken=access-token")
                )))
                .andExpect(cookie().value("refreshToken", "refresh-token"));
    }

    @Test
    @DisplayName("콜백이 NEW_MEMBER면 쿠키 없이 appleSignupToken만 담아 리다이렉트한다")
    void callback_newMember_redirectsWithSignupToken() throws Exception {
        given(appleWebLoginStateRepository.consume("valid-state")).willReturn(Optional.of("raw-nonce"));
        given(appleLoginQueryService.login("id-token", "raw-nonce")).willReturn(
                new AppleLoginResult(AppleLoginResultType.NEW_MEMBER, null, null, null,
                        null, "apple-signup-token", false, null)
        );

        mockMvc.perform(post("/api/v1/auth/apple/web/callback")
                        .param("state", "valid-state")
                        .param("id_token", "id-token"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("resultType=NEW_MEMBER"),
                        org.hamcrest.Matchers.containsString("appleSignupToken=apple-signup-token")
                )))
                .andExpect(cookie().doesNotExist("refreshToken"));

        then(refreshTokenCookieFactory).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("PENDING_PROFILE이면 signupToken을 담아 리다이렉트한다")
    void callback_pendingProfile_redirectsWithSignupToken() throws Exception {
        given(appleWebLoginStateRepository.consume("valid-state")).willReturn(Optional.of("raw-nonce"));
        given(appleLoginQueryService.login("id-token", "raw-nonce")).willReturn(
                new AppleLoginResult(AppleLoginResultType.PENDING_PROFILE, 1L, null, null,
                        "signup-token", null, true, null)
        );

        mockMvc.perform(post("/api/v1/auth/apple/web/callback")
                        .param("state", "valid-state")
                        .param("id_token", "id-token"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("resultType=PENDING_PROFILE"),
                        org.hamcrest.Matchers.containsString("signupToken=signup-token"),
                        org.hamcrest.Matchers.containsString("restored=true")
                )));
    }

    @Test
    @DisplayName("알 수 없거나 만료된 state면 401을 반환하고 Apple 검증을 시도하지 않는다")
    void callback_unknownState_returnsUnauthorized() throws Exception {
        given(appleWebLoginStateRepository.consume("unknown-state")).willReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/apple/web/callback")
                        .param("state", "unknown-state")
                        .param("id_token", "id-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN.getCode()));

        then(appleLoginQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("identity token 검증에 실패하면 예외가 그대로 전파된다")
    void callback_invalidIdentityToken_propagatesException() throws Exception {
        given(appleWebLoginStateRepository.consume("valid-state")).willReturn(Optional.of("raw-nonce"));
        willThrow(new CustomException(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN))
                .given(appleLoginQueryService).login("bad-id-token", "raw-nonce");

        mockMvc.perform(post("/api/v1/auth/apple/web/callback")
                        .param("state", "valid-state")
                        .param("id_token", "bad-id-token"))
                .andExpect(status().isUnauthorized());
    }
}
