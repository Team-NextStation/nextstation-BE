package com.cotato.nextstation.domain.auth.dto.response;

import com.cotato.nextstation.domain.member.entity.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답")
public record LoginResponse(

        @Schema(description = "회원 id", example = "1")
        Long memberId,

        @Schema(description = "API 요청 시 Authorization: Bearer 헤더에 담아 보내는 access token. 1시간 후 만료된다.", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "이번 로그인으로 탈퇴 상태였던 계정이 복구되었는지 여부. true면 '계정이 복구되었습니다' 안내를 노출한다.", example = "false")
        boolean restored,

        @Schema(description = "회원 권한. ADMIN이면 관리자 메뉴를 노출한다.", example = "USER")
        MemberRole role
) {
}
