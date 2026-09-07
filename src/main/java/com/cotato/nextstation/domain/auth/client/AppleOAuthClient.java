package com.cotato.nextstation.domain.auth.client;

import com.cotato.nextstation.domain.auth.client.dto.AppleIdentityToken;
import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.util.AppleNonceHasher;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

// Apple identity token(iOS 네이티브 Sign In with Apple이 발급한 JWT) 검증 전용 클라이언트.
// 카카오와 달리 토큰교환/사용자정보조회 API를 호출하지 않는다 - identity token은 클라이언트가 이미 들고 있고,
// 우리는 Apple JWKS로 서명(RS256)과 iss/aud/exp/nonce 클레임을 검증한다.
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

    public AppleOAuthClient(@Value("${apple.oauth.allowed-audiences}") List<String> allowedAudiences,
                             @Value("${apple.oauth.web-client-id:}") String webClientId) {

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

        // web-client-id(Services ID)를 allowed-audiences에 자동으로 병합한다. 이 둘을 별도 설정값으로 두고
        // 사람이 직접 맞추게 하면, web-client-id만 채우고 allowed-audiences에 추가하는 걸 깜빡하는 실수가
        // 배포 시점이 아니라 실제 웹 로그인 요청이 들어왔을 때 401로만 드러난다 - 그걸 원천 차단한다.
        Set<String> mergedAudiences = new HashSet<>(allowedAudiences);
        if (!webClientId.isBlank()) {
            mergedAudiences.add(webClientId);
        }
        this.allowedAudiences = Set.copyOf(mergedAudiences);
    }

    // 서명(RS256) + iss/aud/exp/nonce 검증까지 통과한 claims에서 우리가 쓰는 값만 추려 반환한다. 위변조·만료·재전송(replay) 토큰은 CustomException(401)으로 거부된다.
    // nonce는 클라이언트(iOS)가 ASAuthorizationAppleIDRequest.nonce에 넣은 값의 원문(raw)이다 -> 탈취된 identity token을 그대로
    // 재전송하는 공격을 막기 위해, 그 원문을 SHA-256 해싱한 값이 토큰 안의 nonce 클레임과 일치하는지 확인한다.
    public AppleIdentityToken verify(String identityToken, String nonce) {

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

        String expectedNonce = claims.get("nonce", String.class);
        if (expectedNonce == null || !expectedNonce.equalsIgnoreCase(AppleNonceHasher.hash(nonce))) {
            log.warn("nonce가 일치하지 않는 Apple identity token(재전송 의심)");
            throw new CustomException(AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN);
        }

        return AppleIdentityToken.from(claims);
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
