package com.cotato.nextstation.domain.auth.client;

import com.cotato.nextstation.domain.auth.client.dto.AppleTokenResponse;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

// Apple REST API 중 identity token 검증 이외의 것(authorizationCode 교환, revoke)을 다루는 클라이언트.
// AppleOAuthClient(JWKS 서명 검증)와 책임이 달라 분리했다 - 이쪽은 client_secret(JWT)로 Apple과 직접 통신한다.
@Slf4j
@Component
public class AppleTokenClient {

    private static final String TOKEN_URI = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URI = "https://appleid.apple.com/auth/revoke";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final AppleClientSecretGenerator clientSecretGenerator;
    private final RestClient restClient;
    private final String clientId;

    public AppleTokenClient(AppleClientSecretGenerator clientSecretGenerator,
                             @Value("${apple.oauth.allowed-audiences}") List<String> allowedAudiences) {
        this.clientSecretGenerator = clientSecretGenerator;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.clientId = allowedAudiences.isEmpty() ? "" : allowedAudiences.get(0);
    }

    // authorizationCode는 1회용이라 재시도 시 이미 소모된 코드로는 실패한다. 신규 가입(최초 Apple 인증) 시점에만 호출한다.
    public AppleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");

        try {
            return restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AppleTokenResponse.class);

        } catch (RestClientException e) {
            log.warn("Apple authorizationCode 교환 실패", e);
            throw new CustomException(GlobalErrorCode.EXTERNAL_API_ERROR);
        }
    }

    // 탈퇴 시 저장해둔 refresh_token을 폐기한다. 이미 폐기된 토큰을 다시 revoke해도 Apple은 보통 200을 반환한다(멱등).
    public void revoke(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");

        try {
            restClient.post()
                    .uri(REVOKE_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException e) {
            log.warn("Apple refresh_token revoke 실패", e);
            throw new CustomException(GlobalErrorCode.EXTERNAL_API_ERROR);
        }
    }
}
