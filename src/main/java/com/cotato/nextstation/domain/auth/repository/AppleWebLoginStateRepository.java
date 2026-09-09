package com.cotato.nextstation.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

// 웹 Apple 로그인의 state(CSRF 방지)와 nonce(원문) 쌍을 잠깐 보관한다.
// 네이티브는 클라이언트(iOS)가 nonce 원문을 들고 있다가 서버로 보내지만,
// 웹은 리다이렉트만 오가서 클라이언트가 원문을 못 들고 있다 -> 서버가 대신 들고 있어야 한다.
@Repository
@RequiredArgsConstructor
public class AppleWebLoginStateRepository {

    private static final String KEY_FORMAT = "auth:apple-web-state:%s";

    // 사용자가 Apple 로그인 화면에서 머무를 수 있는 시간을 넉넉히 잡되, 탈취된 state가 오래 살아있지 않게 짧게 유지한다.
    private static final Duration EXPIRATION = Duration.ofMinutes(10);

    private final RedisTemplate<String, String> redisTemplate;

    public void save(String state, String nonce) {
        redisTemplate.opsForValue().set(key(state), nonce, EXPIRATION);
    }

    // 콜백은 한 번만 유효해야 하므로 조회와 동시에 지운다(재전송 방지).
    public Optional<String> consume(String state) {
        String nonce = redisTemplate.opsForValue().getAndDelete(key(state));
        return Optional.ofNullable(nonce);
    }

    private String key(String state) {
        return KEY_FORMAT.formatted(state);
    }
}
