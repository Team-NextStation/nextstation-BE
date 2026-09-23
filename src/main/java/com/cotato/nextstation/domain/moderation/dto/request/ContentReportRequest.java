package com.cotato.nextstation.domain.moderation.dto.request;

import com.cotato.nextstation.domain.moderation.enums.ReportReason;
import com.cotato.nextstation.domain.moderation.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "콘텐츠 신고 요청")
public record ContentReportRequest(

        @Schema(
                description = "신고 대상 종류", example = "PLACE_REVIEW",
                allowableValues = {"JOURNAL", "PLACE_REVIEW"}
        )
        @NotNull(message = "신고 대상 종류는 필수입니다.")
        ReportTargetType targetType,

        @Schema(description = "신고 대상 id", example = "501")
        @NotNull(message = "신고 대상 id는 필수입니다.")
        Long targetId,

        @Schema(
                description = "신고 사유", example = "SPAM_AD",
                allowableValues = {"ABUSIVE_CONTENT", "SPAM_AD", "HATE_OR_OFFENSIVE",
                        "IRRELEVANT", "ETC"}
        )
        @NotNull(message = "신고 사유는 필수입니다.")
        ReportReason reason
) {
}
