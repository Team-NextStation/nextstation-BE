package com.cotato.nextstation.domain.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// Apple에 보내는 nonce는 원문을 SHA-256으로 해싱한 값이어야 한다(Apple 문서 권고 사항) -
// identity token의 nonce 클레임에는 이 해시값이 그대로 들어있어, 검증 쪽(AppleOAuthClient)과 발급 쪽
// (네이티브는 클라이언트가, 웹은 AppleWebAuthController가 authorize URL을 만들 때) 양쪽에서 같은 로직을 쓴다.
public final class AppleNonceHasher {

    private static final String ALGORITHM = "SHA-256";

    private AppleNonceHasher() {
    }

    public static String hash(String rawNonce) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashed = digest.digest(rawNonce.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // 표준 JDK가 SHA-256을 지원 안 하는 환경은 없다고 가정한다 - 발생하면 배포 환경 자체가 이상한 것.
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
