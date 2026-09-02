package com.cotato.nextstation.domain.auth.client;

import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

// Apple identity token(iOS 네이티브 Sign In with Apple이 발급한 JWT) 검증 전용 클라이언트.
// 카카오와 달리 토큰교환/사용자정보조회 API를 호출하지 않는다 - identity token은 클라이언트가 이미 들고 있고,
// 우리는 Apple JWKS로 서명(RS256)과 iss/aud/exp 클레임만 검증한다.
@Slf4j
@Component
public class AppleOAuthClient {

    private static final String JWKS_URI = "https://appleid.apple.com/auth/keys";
    private static final String ISSUER = "https://appleid.apple.com";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    // Apple 키는 사실상 거의 안 바뀌므로 주기적 갱신 대신 "모르는 kid를 만났을 때만" 갱신한다.
    // 이 간격은 위조된 kid를 계속 흘려보내 Apple JWKS 엔드포인트를 두드리게 하는 남용을 막는 용도.
    private static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(60);

    private final RestClient restClient;

    // aud 검증은 허용 목록(Set)으로 관리한다 -> 나중에 Services ID(웹) aud를 추가할 때 여기만 늘리면 된다.
    private final Set<String> allowedAudiences;

    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile Map<String, Key> cachedKeysByKid = Map.of();
    private volatile Instant lastRefreshedAt = Instant.EPOCH;

    public AppleOAuthClient(@Value("${apple.oauth.allowed-audiences}") List<String> allowedAudiences) {

        // 목록이 비면 요청마다 런타임에 터지므로 부팅 시점에 실패시킨다
        if (allowedAudiences.isEmpty()) {
            throw new IllegalStateException("apple.oauth.allowed-audiences가 비어 있습니다.");
        }

        // RestClientAutoConfiguration이 RestClient.Builder 빈을 안 만들어줘서 직접 생성.
        // 기본 factory는 타임아웃이 사실상 무제한이라, Apple 응답이 느려지면 요청 스레드가 오래 붙잡힐 수 있어 명시적으로 설정한다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.allowedAudiences = Set.copyOf(allowedAudiences);
    }

    // 서명(RS256) + iss/aud/exp 검증까지 통과한 claims를 반환한다. 위변조·만료 토큰은 CustomException(401)으로 거부된다.
    public Claims verify(String identityToken) {

        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(this::locateKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

        } catch (ExpiredJwtException e) {
            throw new CustomException(AuthErrorCode.APPLE_IDENTITY_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Apple identity token 검증 실패: {}", e.getMessage());
            throw new CustomException(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN);
        }

        Set<String> audience = claims.getAudience();
        if (audience == null || audience.stream().noneMatch(allowedAudiences::contains)) {
            log.warn("허용되지 않은 aud의 Apple identity token: aud={}", audience);
            throw new CustomException(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN);
        }

        return claims;
    }

    private Key locateKey(Header header) {

        if (!(header instanceof JwsHeader jwsHeader) || jwsHeader.getKeyId() == null) {
            throw new CustomException(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN);
        }
        String kid = jwsHeader.getKeyId();

        Key key = cachedKeysByKid.get(kid);
        if (key == null) {
            key = refreshKeysIfStale().get(kid);
        }
        if (key == null) {
            log.warn("알 수 없는 kid의 Apple identity token: kid={}", kid);
            throw new CustomException(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN);
        }
        return key;
    }

    private Map<String, Key> refreshKeysIfStale() {
        refreshLock.lock();
        try {
            // 락 대기 중 다른 스레드가 이미 갱신했을 수 있고, 최근에 갱신했는데도 없는 kid면 재요청해도 소용없다(남용 방지)
            if (Duration.between(lastRefreshedAt, Instant.now()).compareTo(MIN_REFRESH_INTERVAL) < 0) {
                return cachedKeysByKid;
            }

            String jwksJson = fetchJwks();
            JwkSet jwkSet = Jwks.setParser().build().parse(jwksJson);

            Map<String, Key> keys = jwkSet.getKeys().stream()
                    .collect(Collectors.toMap(Jwk::getId, Jwk::toKey));

            cachedKeysByKid = keys;
            lastRefreshedAt = Instant.now();
            return keys;

        } finally {
            refreshLock.unlock();
        }
    }

    private String fetchJwks() {
        try {
            return restClient.get()
                    .uri(JWKS_URI)
                    .retrieve()
                    .body(String.class);

        } catch (RestClientException e) {
            log.warn("Apple JWKS 조회 실패", e);
            throw new CustomException(GlobalErrorCode.EXTERNAL_API_ERROR);
        }
    }
}
