package com.cotato.nextstation.domain.moderation.dto.request;

import com.cotato.nextstation.domain.moderation.enums.ReportReason;
import com.cotato.nextstation.domain.moderation.enums.ReportTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentReportRequestTest {

    @Test
    @DisplayName("기타 사유인데 신고 내용이 비어 있으면 검증에 걸린다")
    void detailRequiredWhenEtc() {
        assertThat(request(ReportReason.ETC, null).isDetailFilledWhenEtc()).isFalse();
        assertThat(request(ReportReason.ETC, "   ").isDetailFilledWhenEtc()).isFalse();
        assertThat(request(ReportReason.ETC, "사칭하고 있습니다.").isDetailFilledWhenEtc()).isTrue();
    }

    @Test
    @DisplayName("기타가 아닌 사유는 신고 내용이 없어도 통과한다")
    void detailOptionalWhenNotEtc() {
        assertThat(request(ReportReason.COMMERCIAL_AD, null).isDetailFilledWhenEtc()).isTrue();
    }

    @Test
    @DisplayName("사유가 없으면 @NotNull이 잡으므로 여기서는 통과시킨다")
    void passWhenReasonIsNull() {
        assertThat(request(null, null).isDetailFilledWhenEtc()).isTrue();
    }

    private ContentReportRequest request(ReportReason reason, String detail) {
        return new ContentReportRequest(ReportTargetType.JOURNAL, 501L, reason, detail);
    }
}
