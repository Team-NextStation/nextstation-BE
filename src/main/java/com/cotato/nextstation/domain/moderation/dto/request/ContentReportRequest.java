package com.cotato.nextstation.domain.moderation.dto.request;

import com.cotato.nextstation.domain.moderation.enums.ReportReason;
import com.cotato.nextstation.domain.moderation.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "콘텐츠 신고 요청")
public record ContentReportRequest(

        @Schema(
                description = "신고 대상 종류", example = "PLACE_REVIEW",
                allowableValues = {"JOURNAL", "PLACE_REVIEW", "PROFILE"}
        )
        @NotNull(message = "신고 대상 종류는 필수입니다.")
        ReportTargetType targetType,

        @Schema(description = "신고 대상 id", example = "501")
        @NotNull(message = "신고 대상 id는 필수입니다.")
        Long targetId,

        @Schema(
                description = """
                        신고 사유. 대상 종류에 따라 사용할 수 있는 값이 다르다.
                        - 공통: ABUSIVE_CONTENT, SPAM_AD
                        - JOURNAL, PLACE_REVIEW 전용: HATE_OR_OFFENSIVE, IRRELEVANT
                        - PROFILE 전용: IMPERSONATION, INAPPROPRIATE_PROFILE
                        """,
                example = "SPAM_AD",
                allowableValues = {"ABUSIVE_CONTENT", "SPAM_AD", "HATE_OR_OFFENSIVE",
                        "IRRELEVANT", "IMPERSONATION", "INAPPROPRIATE_PROFILE"}
        )
        @NotNull(message = "신고 사유는 필수입니다.")
        ReportReason reason
) {
}
