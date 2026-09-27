package com.cotato.nextstation.domain.auth.client;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Apple의 실제 .p8 키가 없어도, 같은 곡선(P-256/ES256)의 테스트용 EC 키 쌍으로 서명·파싱 로직 자체를 검증할 수 있다.
class AppleClientSecretGeneratorTest {

    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";

    private KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256); // P-256 곡선 -> ES256과 매칭
        return generator.generateKeyPair();
    }

    private String toPem(KeyPair keyPair) {
        String base64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        return PEM_HEADER + "\n" + base64 + "\n" + PEM_FOOTER;
    }

    @Test
    @DisplayName("team-id/key-id/private-key가 모두 설정되어 있으면 ES256 서명 JWT를 생성한다")
    void generate_success() throws Exception {
        // given
        KeyPair keyPair = generateEcKeyPair();
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                "TEAM1234ID", "KEY1234ID", toPem(keyPair), List.of("com.cotato.nextstation"));

        // when
        String clientSecret = generator.generate();

        // then
        Claims claims = Jwts.parser()
                .verifyWith((PublicKey) keyPair.getPublic())
                .build()
                .parseSignedClaims(clientSecret)
                .getPayload();

        assertThat(claims.getIssuer()).isEqualTo("TEAM1234ID");
        assertThat(claims.getSubject()).isEqualTo("com.cotato.nextstation");
        assertThat(claims.getAudience()).containsExactly("https://appleid.apple.com");

        JwsHeader header = Jwts.parser()
                .verifyWith((PublicKey) keyPair.getPublic())
                .build()
                .parseSignedClaims(clientSecret)
                .getHeader();
        assertThat(header.getKeyId()).isEqualTo("KEY1234ID");
    }

    @Test
    @DisplayName("리터럴 개행(\\n)으로 이스케이프된 PEM도 정상 파싱한다 - 환경변수로 넘어올 때 흔한 형태")
    void generate_escapedNewlinePem() throws Exception {
        // given
        KeyPair keyPair = generateEcKeyPair();
        String escapedPem = toPem(keyPair).replace("\n", "\\n");
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                "TEAM1234ID", "KEY1234ID", escapedPem, List.of("com.cotato.nextstation"));

        // when & then - 파싱 실패 없이 서명까지 성공해야 한다
        assertThat(generator.generate()).isNotBlank();
    }

    @Test
    @DisplayName("team-id가 비어 있으면 예외가 발생한다")
    void generate_missingTeamId() throws Exception {
        // given
        KeyPair keyPair = generateEcKeyPair();
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                "", "KEY1234ID", toPem(keyPair), List.of("com.cotato.nextstation"));

        // when & then
        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("private-key가 비어 있으면 예외가 발생한다")
    void generate_missingPrivateKey() {
        // given
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                "TEAM1234ID", "KEY1234ID", "", List.of("com.cotato.nextstation"));

        // when & then
        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("private-key 형식이 깨져 있으면 예외가 발생한다")
    void generate_malformedPrivateKey() {
        // given
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                "TEAM1234ID", "KEY1234ID", PEM_HEADER + "\nnot-a-valid-base64-key\n" + PEM_FOOTER,
                List.of("com.cotato.nextstation"));

        // when & then
        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
    }
}
