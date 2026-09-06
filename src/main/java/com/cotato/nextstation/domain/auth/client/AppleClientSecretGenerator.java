package com.cotato.nextstation.domain.auth.client;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

// Apple REST API(토큰 교환·revoke)에 필요한 client_secret은 Apple이 발급해주는 고정값이 아니라,
// 우리가 매번 ES256으로 서명해서 만드는 JWT다. Team ID/Key ID/.p8 프라이빗 키는 Apple Developer
// 콘솔에서 "Sign In with Apple" capability로 발급받은 Key 하나로 얻는다(allowed-audiences와 별개 크레덴셜).
@Component
public class AppleClientSecretGenerator {

    private static final String AUDIENCE = "https://appleid.apple.com";

    // Apple 문서상 exp는 최대 6개월까지 허용하지만, 매 요청 직전에 새로 만들어 쓰므로 짧게 잡아 유출 시 악용 창을 최소화한다.
    private static final Duration CLIENT_SECRET_EXPIRATION = Duration.ofMinutes(5);

    private final String teamId;
    private final String keyId;
    private final String rawPrivateKey;
    private final String clientId;

    public AppleClientSecretGenerator(@Value("${apple.oauth.team-id:}") String teamId,
                                       @Value("${apple.oauth.key-id:}") String keyId,
                                       @Value("${apple.oauth.private-key:}") String rawPrivateKey,
                                       @Value("${apple.oauth.allowed-audiences}") List<String> allowedAudiences) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.rawPrivateKey = rawPrivateKey;
        // client_secret의 sub 클레임은 identity token의 aud와 동일해야 한다 -> 네이티브 Bundle ID를 그대로 쓴다.
        // 웹 Services ID를 추가로 지원하게 되면 어떤 클라이언트로 교환하는지에 따라 sub를 구분해야 한다.
        this.clientId = allowedAudiences.isEmpty() ? "" : allowedAudiences.get(0);
    }

    // team-id/key-id/private-key는 Account Holder가 Apple Developer 콘솔에서 발급하는 값이라, 발급 전에는
    // 비어 있을 수 있다. AppleOAuthClient(allowed-audiences)와 달리 로그인/가입의 핵심 경로가 아니라서
    // 부팅 시점에 막지 않고, 실제로 revoke/토큰 교환을 시도하는 시점에만 지연 검증한다.
    public String generate() {
        if (teamId.isBlank() || keyId.isBlank() || rawPrivateKey.isBlank()) {
            throw new IllegalStateException(
                    "apple.oauth.team-id/key-id/private-key가 설정되지 않았습니다. Apple Sign In Key 발급 후 채워주세요.");
        }

        PrivateKey privateKey = parsePrivateKey(rawPrivateKey);
        Instant now = Instant.now();

        return Jwts.builder()
                .header().add("kid", keyId).and()
                .issuer(teamId)
                .audience().add(AUDIENCE).and()
                .subject(clientId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(CLIENT_SECRET_EXPIRATION)))
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    // .p8 파일은 PEM(PKCS#8) 형식이다. 환경변수에는 줄바꿈이 리터럴 "\n"으로 이스케이프돼 들어올 수 있어 둘 다 처리한다.
    private PrivateKey parsePrivateKey(String rawPrivateKey) {
        try {
            String base64Body = rawPrivateKey
                    .replace("\\n", "\n")
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s", "");

            byte[] decoded = Base64.getDecoder().decode(base64Body);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePrivate(keySpec);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "apple.oauth.private-key 파싱에 실패했습니다. .p8 파일 내용이 올바른지 확인하세요.", e);
        }
    }
}
