package com.cotato.nextstation.domain.auth.dto.response;

import com.cotato.nextstation.domain.auth.service.result.AppleLoginResultType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Apple 로그인 결과. resultType으로 분기해서 나머지 필드를 읽는다.")
public record AppleLoginResponse(

        @Schema(description = "NEW_MEMBER(신규 회원)/PENDING_PROFILE(프로필 설정 미완료)/LOGIN_SUCCESS(로그인 완료)")
        AppleLoginResultType resultType,

        @Schema(description = "회원 id. PENDING_PROFILE·LOGIN_SUCCESS일 때만 값 있음", example = "1")
        Long memberId,

        @Schema(description = "LOGIN_SUCCESS일 때만 발급되는 access token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "PENDING_PROFILE일 때만 발급. /profile 호출 시 Authorization: Bearer 헤더에 사용", example = "eyJhbGciOiJIUzI1NiJ9...")
        String signupToken,

        @Schema(description = "NEW_MEMBER일 때만 발급. 약관 동의 후 /apple/signup 호출 시 사용", example = "eyJhbGciOiJIUzI1NiJ9...")
        String appleSignupToken,

        @Schema(description = "이번 로그인으로 탈퇴 상태였던 계정이 복구되었는지 여부. true면 '계정이 복구되었습니다' 안내를 노출한다.", example = "false")
        boolean restored
) {
}
