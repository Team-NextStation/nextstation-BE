package com.cotato.nextstation.domain.report.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * 지표 리포트를 디스코드 웹훅으로 전송한다.
 * 에러 로그 알림(logback DiscordAppender)과는 채널·URL이 분리되어 있다.
 * <p>
 * URL이 비어 있으면(local 등 웹훅을 쓰지 않는 환경) 전송을 건너뛴다.
 */
@Slf4j
@Component
public class DiscordWebhookClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final String webhookUrl;

    public DiscordWebhookClient(@Value("${report.discord.webhook-url:}") String webhookUrl) {

        // 기본 factory는 타임아웃이 사실상 무제한이라 웹훅이 느려지면 스케줄러 스레드가 오래 붙잡힌다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.webhookUrl = webhookUrl;
    }

    public void send(Map<String, Object> payload) {
        send(webhookUrl, payload);
    }

    /**
     * 채널을 지정해 전송한다.
     * 전송 실패는 호출자에게 전파하지 않는다. 알림은 원래 작업의 성공 여부를 바꾸지 않는다.
     */
    public void send(String webhookUrl, Map<String, Object> payload) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("디스코드 웹훅 URL 미설정, 전송 생략");
            return;
        }

        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("디스코드 리포트 전송 완료");
        } catch (RestClientException e) {
            log.warn("디스코드 리포트 전송 실패: message={}", e.getMessage());
        }
    }
}
