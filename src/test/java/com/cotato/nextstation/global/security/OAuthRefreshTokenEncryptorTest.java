package com.cotato.nextstation.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthRefreshTokenEncryptorTest {

    private static final String SECRET = "test-secret-not-real";
    private static final String SALT = "e5ccd50dd8fa49fba661b88ed4400b2f"; // hex 문자열이어야 한다

    private OAuthRefreshTokenEncryptor encryptor() {
        return new OAuthRefreshTokenEncryptor(SECRET, SALT);
    }

    @Test
    @DisplayName("암호화한 값을 복호화하면 원문과 같다")
    void encryptThenDecrypt_roundTrip() {
        // given
        String plainText = "apple-refresh-token-abc123";

        // when
        String encrypted = encryptor().encrypt(plainText);
        String decrypted = encryptor().decrypt(encrypted);

        // then
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("같은 평문을 암호화해도 매번 다른 암호문이 나온다 - AES-GCM의 랜덤 IV 덕분")
    void encrypt_producesDifferentCipherTextEachTime() {
        // given
        String plainText = "apple-refresh-token-abc123";
        OAuthRefreshTokenEncryptor encryptor = encryptor();

        // when
        String first = encryptor.encrypt(plainText);
        String second = encryptor.encrypt(plainText);

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("암호문이 변조되면 복호화가 실패한다 - AES-GCM(stronger)은 인증된 암호화라 무결성이 깨지면 예외를 던진다")
    void decrypt_tamperedCipherText_throws() {
        // given
        String encrypted = encryptor().encrypt("apple-refresh-token-abc123");
        // hex 문자열의 마지막 한 글자를 바꿔 암호문(태그 포함)을 변조한다
        char lastChar = encrypted.charAt(encrypted.length() - 1);
        char replacement = lastChar == '0' ? '1' : '0';
        String tampered = encrypted.substring(0, encrypted.length() - 1) + replacement;

        // when & then
        assertThatThrownBy(() -> encryptor().decrypt(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("secret이 비어 있으면 생성 시점에 실패한다")
    void constructor_emptySecret() {
        assertThatThrownBy(() -> new OAuthRefreshTokenEncryptor("", SALT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("salt가 비어 있으면 생성 시점에 실패한다")
    void constructor_emptySalt() {
        assertThatThrownBy(() -> new OAuthRefreshTokenEncryptor(SECRET, ""))
                .isInstanceOf(IllegalStateException.class);
    }
}
