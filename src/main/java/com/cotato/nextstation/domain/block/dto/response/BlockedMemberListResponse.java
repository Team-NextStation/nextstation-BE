package com.cotato.nextstation.domain.block.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "차단한 사용자 목록 조회 응답")
public record BlockedMemberListResponse(

        @Schema(description = "차단한 사용자 목록 (최근 차단순)")
        List<BlockedMemberResponse> members
) {
}
