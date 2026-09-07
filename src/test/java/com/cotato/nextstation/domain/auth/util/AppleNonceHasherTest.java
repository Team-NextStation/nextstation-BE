package com.cotato.nextstation.domain.auth.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppleNonceHasherTest {

    @Test
    @DisplayName("nonce는 SHA-256 해시를 소문자 hex로 인코딩한다(Apple identity token의 nonce 클레임 형식과 동일)")
    void hash_sha256Hex() {
        // "" (빈 문자열)의 SHA-256 값은 잘 알려진 고정 해시값이라 검증용으로 쓴다.
        String hashed = AppleNonceHasher.hash("");

        assertThat(hashed).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("같은 원문은 항상 같은 해시를 만든다(네이티브 클라이언트/웹 콜백 양쪽에서 결과가 일치해야 한다)")
    void hash_deterministic() {
        assertThat(AppleNonceHasher.hash("raw-nonce")).isEqualTo(AppleNonceHasher.hash("raw-nonce"));
    }
}
