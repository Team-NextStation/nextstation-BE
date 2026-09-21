package com.cotato.nextstation.domain.report.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;

// 웹훅 전송 실패가 스케줄러로 전파되면 리포트 미전송이 배치 자체를 죽인다. 그 경로만 검증한다.
class DiscordWebhookClientTest {

    // 아무도 듣지 않는 포트라 즉시 연결 실패한다
    private static final String UNREACHABLE_WEBHOOK_URL = "http://127.0.0.1:1/webhook";

    @Test
    @DisplayName("웹훅 전송이 실패해도 예외를 전파하지 않는다")
    void send_failureIsSwallowed() {
        DiscordWebhookClient client = new DiscordWebhookClient(UNREACHABLE_WEBHOOK_URL);

        assertThatCode(() -> client.send(Map.of("content", "리포트"))).doesNotThrowAnyException();
    }
}
