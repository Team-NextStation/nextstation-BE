package com.cotato.nextstation.domain.auth.client;

import com.cotato.nextstation.domain.auth.client.dto.KakaoApiErrorResponse;
import com.cotato.nextstation.domain.auth.client.dto.KakaoErrorResponse;
import com.cotato.nextstation.domain.auth.client.dto.KakaoTokenResponse;
import com.cotato.nextstation.domain.auth.client.dto.KakaoUserInfoResponse;
import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

// 카카오 OAuth 서버(kauth/kapi.kakao.com)와 직접 통신하는 클라이언트: 인가코드 교환 + 사용자 정보 조회
@Slf4j
@Component
public class KakaoOAuthClient {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me?secure_resource=true";
    private static final String UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink";
    private static final String ADMIN_KEY_PREFIX = "KakaoAK ";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    // 사용자의 인가코드 문제가 아니라 우리 앱의 카카오 콘솔 설정/연동 문제로 봐야 하는 error_code
    private static final Set<String> MISCONFIGURATION_ERROR_CODES = Set.of("KOE303", "KOE237");

    private static final int NOT_LINKED_ERROR_CODE = -101;

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final List<String> redirectUris;
    private final String adminKey;

    public KakaoOAuthClient(@Value("${kakao.oauth.client-id}") String clientId,
                             @Value("${kakao.oauth.client-secret:}") String clientSecret,
                             @Value("${kakao.oauth.redirect-uris}") List<String> redirectUris,
                             @Value("${kakao.admin-key}") String adminKey) {

        // 목록이 비면 요청마다 런타임에 터지므로 부팅 시점에 실패시킨다
        if (redirectUris.isEmpty()) {
            throw new IllegalStateException("kakao.oauth.redirect-uris가 비어 있습니다.");
        }

        // RestClientAutoConfiguration이 RestClient.Builder 빈을 안 만들어줘서 직접 생성.
        // 기본 factory는 타임아웃이 사실상 무제한이라, 카카오 응답이 느려지면 요청 스레드가 오래 붙잡힐 수 있어 명시적으로 설정한다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUris = List.copyOf(redirectUris);
        this.adminKey = adminKey;
    }

    // code는 1회용/단기 만료라 재사용 시 카카오가 4xx를 반환한다.
    // redirectUri는 인가코드 발급 때 쓴 값과 완전히 동일해야 하며, 다르면 카카오가 KOE303을 반환한다.
    public KakaoTokenResponse exchangeToken(String code, String redirectUri) {

        String resolvedRedirectUri = resolveRedirectUri(redirectUri);

        // 카카오 토큰 엔드포인트는 JSON이 아니라 form-urlencoded로 받는다
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);

        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        form.add("redirect_uri", resolvedRedirectUri);
        form.add("code", code);

        try {
            return restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);

        } catch (RestClientResponseException e) {

            if (e.getStatusCode().is4xxClientError()) {
                throw mapTokenExchange4xx(e);
            }
            log.warn("카카오 토큰 교환 실패(5xx): status={}", e.getStatusCode());
            throw new CustomException(GlobalErrorCode.EXTERNAL_API_ERROR);

        } catch (RestClientException e) {
            log.warn("카카오 토큰 교환 중 통신 오류", e);
            throw new CustomException(GlobalErrorCode.EXTERNAL_API_ERROR);
        }
    }

    // redirectUri는 선택값이라 없으면 대표 URI로 대체한다.
    // 값이 온 경우에는 반드시 정확히 일치하는지만 확인한다. prefix 비교로 완화하면
    // 등록 URI로 시작하는 외부 도메인이 통과해 인가코드가 유출될 수 있다.
    private String resolveRedirectUri(String redirectUri) {

        if (redirectUri == null || redirectUri.isBlank()) {
            return redirectUris.get(0);
        }
        if (!redirectUris.contains(redirectUri)) {
            log.warn("허용목록에 없는 redirect_uri로 토큰 교환 시도: redirectUri={}", redirectUri);
            throw new CustomException(AuthErrorCode.UNREGISTERED_REDIRECT_URI);
        }
        return redirectUri;
    }

    // 카카오 응답의 error/error_code로 "사용자 인가코드 문제(재사용·만료 등)"와 "우리 앱 설정/연동 문제"를 구분한다.
    // error_description은 인가코드 원문을 그대로 담고 있는 경우가 있어 절대 로그에 남기지 않는다.
    private CustomException mapTokenExchange4xx(RestClientResponseException e) {
        KakaoErrorResponse error = parseErrorBody(e, KakaoErrorResponse.class);

        if (error == null) {
            log.warn("카카오 토큰 교환 실패(4xx, 본문 파싱 불가): status={}", e.getStatusCode());
            return new CustomException(AuthErrorCode.INVALID_KAKAO_CODE);
        }

        if ("invalid_client".equals(error.error()) || MISCONFIGURATION_ERROR_CODES.contains(error.errorCode())) {
            log.error("카카오 OAuth 설정/연동 오류: status={}, error={}, errorCode={}",
                    e.getStatusCode(), error.error(), error.errorCode());
            return new CustomException(AuthErrorCode.KAKAO_OAUTH_MISCONFIGURED);
        }

        log.warn("카카오 토큰 교환 실패(4xx): status={}, error={}, errorCode={}",
                e.getStatusCode(), error.error(), error.errorCode());
        return new CustomException(AuthErrorCode.INVALID_KAKAO_CODE);
    }

    private <T> T parseErrorBody(RestClientResponseException e, Class<T> type) {
        try {
            return e.getResponseBodyAs(type);
        } catch (Exception parseException) {
            return null;
        }
    }

    public KakaoUserInfoResponse fetchUserInfo(String kakaoAccessToken) {
        try {
            return restClient.get()
                    .uri(USER_INFO_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserInfoResponse.class);

        } catch (RestClientException e) {
            log.warn("카카오 사용자 정보 조회 실패", e);
            throw new CustomException(GlobalErrorCode.EXTERNAL_API_ERROR);
        }
    }

    public boolean unlink(String providerUserId) {

        if (adminKey.isBlank()) {
            log.error("카카오 어드민 키가 비어 있어 연결 해제를 건너뛴다: providerUserId={}", providerUserId);
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", providerUserId);

        try {
            restClient.post()
                    .uri(UNLINK_URI)
                    .header(HttpHeaders.AUTHORIZATION, ADMIN_KEY_PREFIX + adminKey)
                    .contentType(new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8))
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();

            log.info("카카오 연결 해제 완료: providerUserId={}", providerUserId);
            return true;

        } catch (RestClientResponseException e) {
            return handleUnlinkFailure(e, providerUserId);

        } catch (RestClientException e) {
            log.warn("카카오 연결 해제 중 통신 오류: providerUserId={}", providerUserId, e);
            return false;
        }
    }

    private boolean handleUnlinkFailure(RestClientResponseException e, String providerUserId) {
        KakaoApiErrorResponse error = parseErrorBody(e, KakaoApiErrorResponse.class);

        if (error != null && error.code() != null && error.code() == NOT_LINKED_ERROR_CODE) {
            log.info("이미 카카오 연결이 해제된 회원: providerUserId={}", providerUserId);
            return true;
        }

        log.warn("카카오 연결 해제 실패: providerUserId={}, status={}, code={}",
                providerUserId, e.getStatusCode(), error == null ? null : error.code());
        return false;
    }
}