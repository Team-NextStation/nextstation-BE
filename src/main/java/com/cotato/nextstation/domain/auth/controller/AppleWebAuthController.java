package com.cotato.nextstation.domain.auth.controller;

import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.repository.AppleWebLoginStateRepository;
import com.cotato.nextstation.domain.auth.service.query.AppleLoginQueryService;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResult;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResultType;
import com.cotato.nextstation.domain.auth.util.AppleNonceHasher;
import com.cotato.nextstation.domain.auth.util.RefreshTokenCookieFactory;
import com.cotato.nextstation.global.exception.CustomException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 웹 브라우저용 Apple 로그인(Services ID, Authorization Code Flow).
 * <p>
 * 네이티브(iOS)는 클라이언트가 이미 identity token을 들고 있어서 검증만 하면 됐지만,
 * 웹은 브라우저가 Apple로 리다이렉트됐다가 돌아오는 구조라 그 과정 자체를 여기서 처리한다.
 * 검증·신규/기존 판별 로직({@link AppleLoginQueryService})은 네이티브와 완전히 동일한 걸 그대로 재사용한다 -
 * identity token만 얻는 방법이 다를 뿐, 얻고 난 뒤의 처리는 다를 이유가 없다.
 * <p>
 * Swagger에 노출하지 않는다({@code @Hidden}) - 이 두 엔드포인트는 API 클라이언트가 직접 호출하는 게 아니라
 * 브라우저 주소창/폼 제출로만 오가는 리다이렉트 엔드포인트라, Swagger UI에서 "Execute"로 눌러봐야 의미가 없다.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/api/v1/auth/apple/web")
public class AppleWebAuthController {

    private static final String AUTHORIZE_URI = "https://appleid.apple.com/auth/authorize";
    private static final int STATE_BYTE_LENGTH = 32;
    private static final int NONCE_BYTE_LENGTH = 32;

    private final AppleLoginQueryService appleLoginQueryService;
    private final AppleWebLoginStateRepository appleWebLoginStateRepository;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final SecureRandom secureRandom = new SecureRandom();

    private final String webClientId;
    private final String webRedirectUri;
    private final String webFrontendRedirectUri;

    public AppleWebAuthController(AppleLoginQueryService appleLoginQueryService,
                                   AppleWebLoginStateRepository appleWebLoginStateRepository,
                                   RefreshTokenCookieFactory refreshTokenCookieFactory,
                                   @Value("${apple.oauth.web-client-id}") String webClientId,
                                   @Value("${apple.oauth.web-redirect-uri}") String webRedirectUri,
                                   @Value("${apple.oauth.web-frontend-redirect-uri}") String webFrontendRedirectUri) {
        this.appleLoginQueryService = appleLoginQueryService;
        this.appleWebLoginStateRepository = appleWebLoginStateRepository;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.webClientId = webClientId;
        this.webRedirectUri = webRedirectUri;
        this.webFrontendRedirectUri = webFrontendRedirectUri;
    }

    // 브라우저를 Apple 로그인 화면으로 보낸다. state(CSRF 방지)와 nonce(재전송 방지) 원문을 생성해
    // Redis에 잠깐 저장해두고, Apple에는 nonce의 해시값만 넘긴다(네이티브 클라이언트와 동일한 규칙).
    @GetMapping("/authorize")
    public void authorize(HttpServletResponse httpResponse) {
        String state = randomToken(STATE_BYTE_LENGTH);
        String nonce = randomToken(NONCE_BYTE_LENGTH);
        appleWebLoginStateRepository.save(state, nonce);

        URI authorizeUri = UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
                .queryParam("client_id", webClientId)
                .queryParam("redirect_uri", webRedirectUri)
                .queryParam("response_type", "code id_token")
                // Apple은 response_type에 id_token이 포함되면 response_mode=form_post를 강제한다.
                .queryParam("response_mode", "form_post")
                .queryParam("scope", "email name")
                .queryParam("state", state)
                .queryParam("nonce", AppleNonceHasher.hash(nonce))
                .build()
                .toUri();

        httpResponse.setStatus(HttpStatus.FOUND.value());
        httpResponse.setHeader(HttpHeaders.LOCATION, authorizeUri.toString());
    }

    // Apple이 로그인 완료 후 여기로 POST(form_post)한다 - 브라우저가 아니라 Apple 서버가 보내는 폼 제출이라
    // Content-Type이 application/x-www-form-urlencoded로 온다.
    @PostMapping("/callback")
    public void callback(@RequestParam("state") String state,
                          @RequestParam("id_token") String identityToken,
                          HttpServletResponse httpResponse) {

        String nonce = appleWebLoginStateRepository.consume(state)
                .orElseThrow(() -> {
                    log.warn("알 수 없거나 만료된 state로 Apple 웹 로그인 콜백 시도");
                    return new CustomException(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN);
                });

        AppleLoginResult result = appleLoginQueryService.login(identityToken, nonce);

        if (result.resultType() == AppleLoginResultType.LOGIN_SUCCESS) {
            ResponseCookie refreshTokenCookie = refreshTokenCookieFactory.create(result.refreshToken());
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
        }

        URI redirectUri = buildFrontendRedirectUri(result);
        httpResponse.setStatus(HttpStatus.FOUND.value());
        httpResponse.setHeader(HttpHeaders.LOCATION, redirectUri.toString());
    }

    // accessToken/signupToken처럼 짧게 사는 값만 쿼리 파라미터로 실어 보낸다 - 프론트가 받자마자 URL에서 지워야 한다.
    // refreshToken은 여기 절대 담지 않는다(이미 httpOnly 쿠키로 내려갔다).
    private URI buildFrontendRedirectUri(AppleLoginResult result) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(webFrontendRedirectUri)
                .queryParam("resultType", result.resultType());

        switch (result.resultType()) {
            case LOGIN_SUCCESS -> builder.queryParam("accessToken", result.accessToken())
                    .queryParam("restored", result.restored());
            case PENDING_PROFILE -> builder.queryParam("signupToken", result.signupToken())
                    .queryParam("restored", result.restored());
            case NEW_MEMBER -> builder.queryParam("appleSignupToken", result.appleSignupToken());
        }

        return builder.build().toUri();
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
