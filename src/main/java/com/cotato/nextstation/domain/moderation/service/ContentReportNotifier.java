package com.cotato.nextstation.domain.moderation.service;

import com.cotato.nextstation.domain.moderation.event.ContentReportedEvent;
import com.cotato.nextstation.domain.report.client.DiscordWebhookClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 접수된 콘텐츠 신고를 디스코드에 연동
@Slf4j
@Component
public class ContentReportNotifier {

    private static final int EMBED_COLOR = 0xEF4444;

    private static final int BODY_MAX_LENGTH = 100;
    private static final String EMPTY_BODY = "_(본문 없음)_";

    private static final DateTimeFormatter REPORTED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");

    private final DiscordWebhookClient discordWebhookClient;
    private final String webhookUrl;

    public ContentReportNotifier(DiscordWebhookClient discordWebhookClient,
                                 @Value("${moderation.discord.webhook-url:}") String webhookUrl) {
        this.discordWebhookClient = discordWebhookClient;
        this.webhookUrl = webhookUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContentReported(ContentReportedEvent event) {

        List<Map<String, Object>> fields = new ArrayList<>(List.of(
                field("대상", "%s\n`%s=%d`".formatted(
                        event.targetType(), event.targetType().getIdLabel(), event.targetId()), true),
                field("사유", event.reason().getLabel(), true),
                field("신고자", "`memberId=%d`".formatted(event.reporterId()), true),
                field("본문", truncate(event.targetBody()), false)
        ));

        if (event.detail() != null && !event.detail().isBlank()) {
            fields.add(field("신고 내용", event.detail(), false));
        }

        Map<String, Object> payload = Map.of("embeds", List.of(Map.of(
                "title", "🚨 콘텐츠 신고 접수 · reportId=%d".formatted(event.reportId()),
                "color", EMBED_COLOR,
                "fields", fields,
                "footer", Map.of("text", LocalDateTime.now(REPORT_ZONE).format(REPORTED_AT_FORMAT)))));

        discordWebhookClient.send(webhookUrl, payload);
    }

    private Map<String, Object> field(String name, String value, boolean inline) {
        return Map.of("name", name, "value", value, "inline", inline);
    }

    private String truncate(String body) {
        if (body == null || body.isBlank()) {
            return EMPTY_BODY;
        }

        return body.length() <= BODY_MAX_LENGTH ? body : body.substring(0, BODY_MAX_LENGTH) + "...";
    }
}
