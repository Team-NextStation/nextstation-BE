package com.cotato.nextstation.domain.auth.repository;

import com.cotato.nextstation.domain.auth.dto.response.AppleLoginResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

// 웹 Apple 로그인 콜백 결과(accessToken 등)를 1회용 code로 교환해주기 위해 잠깐 보관한다.
// accessToken을 콜백 리다이렉트 쿼리 파라미터에 그대로 실으면 브라우저 히스토리·리퍼러·서버 로그에
// 평문으로 남을 수 있어서, 리다이렉트에는 이 code만 담고 실제 값은 별도 교환 API(POST)로만 내려준다.
@Slf4j
@Repository
@RequiredArgsConstructor
public class AppleWebLoginCodeRepository {

    private static final String KEY_FORMAT = "auth:apple-web-code:%s";

    // 프론트가 리다이렉트 직후 바로 교환할 값이라 길게 둘 이유가 없다 - 탈취된 code의 유효 시간을 최소화한다.
    private static final Duration EXPIRATION = Duration.ofSeconds(60);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(String code, AppleLoginResponse payload) {
        try {
            redisTemplate.opsForValue().set(key(code), objectMapper.writeValueAsString(payload), EXPIRATION);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AppleLoginResponse 직렬화 실패", e);
        }
    }

    // 1회만 유효해야 하므로 조회와 동시에 지운다(재전송/재사용 방지).
    public Optional<AppleLoginResponse> consume(String code) {
        String json = redisTemplate.opsForValue().getAndDelete(key(code));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, AppleLoginResponse.class));
        } catch (JsonProcessingException e) {
            log.warn("저장된 AppleLoginResponse 역직렬화 실패: code={}", code, e);
            return Optional.empty();
        }
    }

    private String key(String code) {
        return KEY_FORMAT.formatted(code);
    }
}
