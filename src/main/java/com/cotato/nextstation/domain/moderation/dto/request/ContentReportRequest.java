package com.cotato.nextstation.domain.moderation.dto.request;

import com.cotato.nextstation.domain.moderation.enums.ReportReason;
import com.cotato.nextstation.domain.moderation.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
                description = "신고 사유", example = "COMMERCIAL_AD",
                allowableValues = {"FALSE_INFORMATION", "COMMERCIAL_AD", "OBSCENE",
                        "VIOLENCE", "PRIVACY_EXPOSURE", "ETC"}
        )
        @NotNull(message = "신고 사유는 필수입니다.")
        ReportReason reason,

        @Schema(description = "사유가 ETC일 때 직접 입력한 내용", example = "다른 이용자를 사칭하고 있습니다.",
                maxLength = 500)
        @Size(max = 500, message = "신고 내용은 최대 500자까지 입력 가능합니다.")
        String detail
) {

    @Schema(hidden = true)
    @AssertTrue(message = "기타 사유를 선택한 경우 신고 내용을 입력해야 합니다.")
    public boolean isDetailFilledWhenEtc() {
        return reason != ReportReason.ETC || (detail != null && !detail.isBlank());
    }
}
