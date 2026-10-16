package com.cotato.nextstation.domain.auth.controller;

import com.cotato.nextstation.domain.auth.dto.response.AppleLoginResponse;
import com.cotato.nextstation.domain.auth.repository.AppleWebLoginCodeRepository;
import com.cotato.nextstation.domain.auth.repository.AppleWebLoginStateRepository;
import com.cotato.nextstation.domain.auth.service.query.AppleLoginQueryService;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResult;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResultType;
import com.cotato.nextstation.domain.auth.util.AppleNonceHasher;
import com.cotato.nextstation.domain.auth.util.RefreshTokenCookieFactory;
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
import java.util.Optional;

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

    private static final int CODE_BYTE_LENGTH = 32;

    private final AppleLoginQueryService appleLoginQueryService;
    private final AppleWebLoginStateRepository appleWebLoginStateRepository;
    private final AppleWebLoginCodeRepository appleWebLoginCodeRepository;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final SecureRandom secureRandom = new SecureRandom();

    private final String webClientId;
    private final String webRedirectUri;
    private final String webFrontendRedirectUri;

    public AppleWebAuthController(AppleLoginQueryService appleLoginQueryService,
                                   AppleWebLoginStateRepository appleWebLoginStateRepository,
                                   AppleWebLoginCodeRepository appleWebLoginCodeRepository,
                                   RefreshTokenCookieFactory refreshTokenCookieFactory,
                                   @Value("${apple.oauth.web-client-id}") String webClientId,
                                   @Value("${apple.oauth.web-redirect-uri}") String webRedirectUri,
                                   @Value("${apple.oauth.web-frontend-redirect-uri}") String webFrontendRedirectUri) {
        this.appleLoginQueryService = appleLoginQueryService;
        this.appleWebLoginStateRepository = appleWebLoginStateRepository;
        this.appleWebLoginCodeRepository = appleWebLoginCodeRepository;
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
    // id_token/error는 둘 다 필수로 받으면 안 된다 - 사용자가 Apple 로그인 화면에서 취소하거나 인증에 실패하면
    // Apple은 id_token 없이 state와 error만 보낸다. 여기서 필수 파라미터로 막아버리면 Spring이 400을 직접
    // 렌더링해서 사용자가 프론트로 돌아가지 못하므로, 둘 다 선택값으로 받아 실패도 프론트로 리다이렉트해준다.
    @PostMapping("/callback")
    public void callback(@RequestParam("state") String state,
                          @RequestParam(value = "id_token", required = false) String identityToken,
                          @RequestParam(value = "error", required = false) String error,
                          HttpServletResponse httpResponse) {

        Optional<String> nonce = appleWebLoginStateRepository.consume(state);
        if (nonce.isEmpty()) {
            log.warn("알 수 없거나 만료된 state로 Apple 웹 로그인 콜백 시도");
            redirectToFrontend(httpResponse, buildFailureRedirectUri("invalid_state"));
            return;
        }
        if (error != null || identityToken == null) {
            log.warn("Apple 웹 로그인 실패 콜백: state={}, error={}", state, error);
            redirectToFrontend(httpResponse, buildFailureRedirectUri(error != null ? error : "missing_id_token"));
            return;
        }

        AppleLoginResult result = appleLoginQueryService.login(identityToken, nonce.get());

        if (result.resultType() == AppleLoginResultType.LOGIN_SUCCESS) {
            ResponseCookie refreshTokenCookie = refreshTokenCookieFactory.create(result.refreshToken());
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
        }

        redirectToFrontend(httpResponse, buildSuccessRedirectUri(result));
    }

    // accessToken 등 민감한 값은 리다이렉트 쿼리 파라미터에 직접 싣지 않는다 - 브라우저 히스토리/리퍼러/서버
    // 접근 로그에 평문으로 남을 수 있기 때문이다. 대신 결과를 1회용 code로 교환해 Redis에 짧게 저장해두고,
    // 프론트에는 code만 넘겨서 별도 POST API(AppleWebLoginExchangeController)로 교환하게 한다.
    private URI buildSuccessRedirectUri(AppleLoginResult result) {
        AppleLoginResponse response = new AppleLoginResponse(
                result.resultType(),
                result.memberId(),
                result.accessToken(),
                result.signupToken(),
                result.appleSignupToken(),
                result.restored(),
                result.role()
        );

        String code = randomToken(CODE_BYTE_LENGTH);
        appleWebLoginCodeRepository.save(code, response);

        return UriComponentsBuilder.fromUriString(webFrontendRedirectUri)
                .queryParam("code", code)
                .build()
                .toUri();
    }

    private URI buildFailureRedirectUri(String error) {
        return UriComponentsBuilder.fromUriString(webFrontendRedirectUri)
                .queryParam("resultType", "FAILURE")
                .queryParam("error", error)
                .build()
                .toUri();
    }

    private void redirectToFrontend(HttpServletResponse httpResponse, URI redirectUri) {
        httpResponse.setStatus(HttpStatus.FOUND.value());
        httpResponse.setHeader(HttpHeaders.LOCATION, redirectUri.toString());
    }

    private String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
