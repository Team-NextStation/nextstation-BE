package com.cotato.nextstation.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

// SocialOauthCredential.refreshToken을 저장 전/조회 후 암복호화하는 전담 컴포넌트.
// Encryptors.delux()는 AES-256-GCM(인증된 암호화, Encryptors.stronger()의 TextEncryptor 버전) 기반이라
// 암호문 무결성까지 검증한다 - Encryptors.text()(AES-CBC)와 달리 암호문이 변조되면 복호화 시점에 예외로 걸러진다.
// 매 encrypt() 호출마다 랜덤 IV를 섞어 넣어 같은 평문이어도 암호문이 매번 달라지므로, 저장된 암호문끼리
// 비교해서 평문을 추측하는 공격에도 안전하다. 결과는 hex 문자열로 나와 DB 컬럼에 바로 저장 가능하다.
//
// secret/salt는 Apple/카카오가 발급하는 값이 아니라 우리가 직접 만드는 애플리케이션 비밀이다.
// 유출되면 저장된 모든 refresh_token이 한꺼번에 복호화되므로, JWT_SECRET과 동급으로 취급해 관리한다.
@Component
public class OAuthRefreshTokenEncryptor {

    private final TextEncryptor textEncryptor;

    public OAuthRefreshTokenEncryptor(@Value("${security.oauth-credential.secret}") String secret,
                                       @Value("${security.oauth-credential.salt}") String salt) {

        if (secret.isBlank() || salt.isBlank()) {
            throw new IllegalStateException("security.oauth-credential.secret/salt가 비어 있습니다.");
        }
        this.textEncryptor = Encryptors.delux(secret, salt);
    }

    public String encrypt(String plainText) {
        return textEncryptor.encrypt(plainText);
    }

    public String decrypt(String cipherText) {
        return textEncryptor.decrypt(cipherText);
    }
}
