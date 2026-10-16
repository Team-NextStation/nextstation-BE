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

// 임베드 구성(줄 배치·자르기·빈 값 처리)을 확인한다.
@ExtendWith(MockitoExtension.class)
class ContentReportNotifierTest {

    private static final String WEBHOOK_URL = "https://discord.test/webhook";

    @Mock
    private DiscordWebhookClient discordWebhookClient;

    @Test
    @DisplayName("본문이 100자를 넘으면 잘라서 보낸다")
    void onContentReported_longBodyIsTruncated() {
        String body = "가".repeat(150);

        String description = descriptionOf(event(body));

        assertThat(description).contains("가".repeat(100) + "...").doesNotContain("가".repeat(101));
    }

    @Test
    @DisplayName("본문이 비어 있어도 줄을 비워두지 않는다")
    void onContentReported_blankBodyHasPlaceholder() {
        String description = descriptionOf(event(null));

        assertThat(lineOf(description, "리뷰 내용")).isNotBlank();
    }

    @Test
    @DisplayName("대상 id를 어느 테이블의 id인지 드러나게 표기한다")
    void onContentReported_targetIdHasLabel() {
        String description = descriptionOf(event("리뷰 본문"));

        assertThat(lineOf(description, "대상")).contains("reviewId=501");
    }

    @Test
    @DisplayName("줄마다 빈 줄을 넣어 간격을 띄운다")
    void onContentReported_linesAreSeparatedByBlankLine() {
        String description = descriptionOf(event("리뷰 본문"));

        assertThat(description.split("\n\n"))
                .extracting(line -> line.substring(0, line.indexOf("**", 2) + 2))
                .containsExactly("**대상**", "**사유**", "**신고자**", "**리뷰 내용**");
    }

    @Test
    @DisplayName("프로필 신고는 제목으로 종류를 드러내고 내용 줄을 만들지 않는다")
    void onContentReported_profileHasNoBodyLine() {
        Map<String, Object> embed = sendAndCaptureEmbed(new ContentReportedEvent(
                12L, 1L, ReportTargetType.PROFILE, 501L, ReportReason.ABUSIVE_CONTENT, "민성"));

        assertThat((String) embed.get("title")).contains("프로필 신고 접수");
        assertThat((String) embed.get("description"))
                .contains("memberId=501")
                .doesNotContain("민성");
    }

    private String descriptionOf(ContentReportedEvent event) {
        return (String) sendAndCaptureEmbed(event).get("description");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sendAndCaptureEmbed(ContentReportedEvent event) {
        new ContentReportNotifier(discordWebhookClient, WEBHOOK_URL).onContentReported(event);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        then(discordWebhookClient).should().send(eq(WEBHOOK_URL), captor.capture());

        List<Map<String, Object>> embeds = (List<Map<String, Object>>) captor.getValue().get("embeds");
        return embeds.get(0);
    }

    private String lineOf(String description, String name) {
        return java.util.Arrays.stream(description.split("\n\n"))
                .filter(line -> line.startsWith("**%s**".formatted(name)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("%s 줄이 없다".formatted(name)));
    }

    private ContentReportedEvent event(String targetBody) {
        return new ContentReportedEvent(12L, 1L, ReportTargetType.PLACE_REVIEW, 501L,
                ReportReason.ABUSIVE_CONTENT, targetBody);
    }
}
