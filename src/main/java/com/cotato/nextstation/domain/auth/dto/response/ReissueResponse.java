package com.cotato.nextstation.domain.auth.dto.response;

import com.cotato.nextstation.domain.member.entity.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "액세스 토큰 재발급 응답")
public record ReissueResponse(

        @Schema(description = "새로 발급된 access token. 1시간 후 만료된다.", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "회원 권한. 재진입이 재발급으로만 이뤄지는 경우에도 관리자 메뉴를 판단할 수 있도록 함께 내려준다.", example = "USER")
        MemberRole role
) {
}
