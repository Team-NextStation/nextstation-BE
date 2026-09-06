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
