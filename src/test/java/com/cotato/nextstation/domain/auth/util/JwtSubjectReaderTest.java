package com.cotato.nextstation.domain.auth.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSubjectReaderTest {

    private String jwt(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes());
        return header + "." + payload + ".signature";
    }

    @Test
    @DisplayName("payload의 sub 클레임을 꺼낸다")
    void readSubject_returnsSubject() {
        String token = jwt("{\"sub\":\"000555.abcdef.0555\",\"aud\":\"com.cotato.nextstation\"}");

        assertThat(JwtSubjectReader.readSubject(token)).isEqualTo("000555.abcdef.0555");
    }

    @Test
    @DisplayName("sub 클레임이 없으면 null을 반환한다")
    void readSubject_noSubClaim_returnsNull() {
        String token = jwt("{\"aud\":\"com.cotato.nextstation\"}");

        assertThat(JwtSubjectReader.readSubject(token)).isNull();
    }

    @Test
    @DisplayName("JWT 형식이 아니면 예외 대신 null을 반환한다")
    void readSubject_malformedToken_returnsNull() {
        assertThat(JwtSubjectReader.readSubject("not-a-jwt")).isNull();
    }

    @Test
    @DisplayName("null 입력이면 예외 대신 null을 반환한다")
    void readSubject_nullInput_returnsNull() {
        assertThat(JwtSubjectReader.readSubject(null)).isNull();
    }
}
