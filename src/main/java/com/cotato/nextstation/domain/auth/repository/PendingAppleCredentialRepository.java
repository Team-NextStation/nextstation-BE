package com.cotato.nextstation.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

// Apple authorizationCode를 로그인 판별 시점(NEW_MEMBER로 갈릴 때)에 미리 교환해둔 암호화된 refresh_token을
// 회원가입이 실제로 완료될 때까지 잠깐 보관한다.
// <p>
// authorizationCode는 1회용이고 수명이 짧다(수 분). 예전엔 이 교환을 /apple/signup 시점(약관 동의 화면을 다
// 보고 난 뒤)까지 미뤄뒀는데, 그 사이 code가 만료되거나 - 교환에 성공해도 그 뒤 로컬 저장이 실패하면 이미
// 소비된 code만 날리는 문제가 있었다. 그래서 code를 받는 가장 이른 시점(로그인)에 바로 교환해 여기 캐싱해두고,
// 가입 시점엔 네트워크 호출 없이 이 값을 그대로 SocialOauthCredential로 옮겨 붙이기만 한다.
@Repository
@RequiredArgsConstructor
public class PendingAppleCredentialRepository {

    private static final String KEY_FORMAT = "auth:pending-apple-credential:%s";

    // appleSignupToken 수명(10분)과 맞춘다 - 그 안에 가입을 완료하지 않으면 어차피 처음부터 다시 로그인해야 한다.
    private static final Duration EXPIRATION = Duration.ofMinutes(10);

    private final RedisTemplate<String, String> redisTemplate;

    public void save(String providerUserId, String encryptedRefreshToken) {
        redisTemplate.opsForValue().set(key(providerUserId), encryptedRefreshToken, EXPIRATION);
    }

    // 가입 완료 시 한 번만 쓰이므로 조회와 동시에 지운다(재사용 방지, 메모리 낭비 방지).
    public Optional<String> consume(String providerUserId) {
        String value = redisTemplate.opsForValue().getAndDelete(key(providerUserId));
        return Optional.ofNullable(value);
    }

    private String key(String providerUserId) {
        return KEY_FORMAT.formatted(providerUserId);
    }
}
