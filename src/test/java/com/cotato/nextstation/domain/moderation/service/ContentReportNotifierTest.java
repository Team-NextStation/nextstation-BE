package com.cotato.nextstation.domain.moderation.service;

import com.cotato.nextstation.domain.moderation.event.ContentReportedEvent;
import com.cotato.nextstation.domain.moderation.enums.ReportReason;
import com.cotato.nextstation.domain.moderation.enums.ReportTargetType;
import com.cotato.nextstation.domain.report.client.DiscordWebhookClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

// 임베드 구성(칸 배치·자르기·빈 값 처리)을 확인한다.
@ExtendWith(MockitoExtension.class)
class ContentReportNotifierTest {

    private static final String WEBHOOK_URL = "https://discord.test/webhook";

    @Mock
    private DiscordWebhookClient discordWebhookClient;

    @Test
    @DisplayName("본문이 100자를 넘으면 잘라서 보낸다")
    void onContentReported_longBodyIsTruncated() {
        String body = "가".repeat(150);

        String value = valueOf(sendAndCaptureFields(event(body)), "본문");

        assertThat(value).isEqualTo("가".repeat(100) + "...");
    }

    @Test
    @DisplayName("본문이 비어 있어도 칸을 비워두지 않는다")
    void onContentReported_blankBodyHasPlaceholder() {
        String value = valueOf(sendAndCaptureFields(event(null)), "본문");

        assertThat(value).isNotBlank();
    }

    @Test
    @DisplayName("대상 id를 어느 테이블의 id인지 드러나게 표기한다")
    void onContentReported_targetIdHasLabel() {
        String value = valueOf(sendAndCaptureFields(event("리뷰 본문")), "대상");

        assertThat(value).contains("PLACE_REVIEW").contains("reviewId=501");
    }

    @Test
    @DisplayName("대상·사유·신고자는 한 줄에 놓이도록 inline으로 보낸다")
    void onContentReported_summaryFieldsAreInline() {
        List<Map<String, Object>> fields = sendAndCaptureFields(event("리뷰 본문"));

        assertThat(fields).filteredOn(field -> Boolean.TRUE.equals(field.get("inline")))
                .extracting(field -> field.get("name"))
                .containsExactly("대상", "사유", "신고자");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sendAndCaptureFields(ContentReportedEvent event) {
        new ContentReportNotifier(discordWebhookClient, WEBHOOK_URL).onContentReported(event);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        then(discordWebhookClient).should().send(eq(WEBHOOK_URL), captor.capture());

        List<Map<String, Object>> embeds = (List<Map<String, Object>>) captor.getValue().get("embeds");
        return (List<Map<String, Object>>) embeds.get(0).get("fields");
    }

    private String valueOf(List<Map<String, Object>> fields, String name) {
        return fields.stream()
                .filter(field -> name.equals(field.get("name")))
                .map(field -> (String) field.get("value"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("%s 칸이 없다".formatted(name)));
    }

    private ContentReportedEvent event(String targetBody) {
        return new ContentReportedEvent(12L, 1L, ReportTargetType.PLACE_REVIEW, 501L,
                ReportReason.ABUSIVE_CONTENT, targetBody);
    }
}
