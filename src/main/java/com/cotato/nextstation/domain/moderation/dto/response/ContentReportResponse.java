package com.cotato.nextstation.domain.moderation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "콘텐츠 신고 접수 결과")
public record ContentReportResponse(
        @Schema(description = "접수된 신고 id", example = "12")
        Long reportId
) {
}
