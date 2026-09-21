package com.cotato.nextstation.domain.auth.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// JWT의 서명을 검증하지 않고 payload의 sub 클레임만 가볍게 꺼낼 때 쓴다.
// Apple 토큰 엔드포인트가 TLS로 직접 내려준 응답(재전송/위조 경로가 없음)의 id_token처럼,
// 이미 신뢰할 수 있는 채널로 받은 값에서 "혹시 다른 사용자 것이 섞이지 않았는지"만 대조하고 싶을 때 쓰는 용도라 -
// 여기서 서명까지 검증해야 하는 경우(클라이언트가 보낸 identityToken 등)에는 절대 쓰면 안 되고 AppleOAuthClient.verify()를 써야 한다.
public final class JwtSubjectReader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JwtSubjectReader() {
    }

    // 형식이 깨졌거나 sub가 없으면 null을 반환한다 - 호출자가 "확인 불가"로 보고 안전하게 처리하도록 예외 대신 null을 쓴다.
    public static String readSubject(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = OBJECT_MAPPER.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            JsonNode sub = payload.get("sub");
            return sub == null ? null : sub.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
