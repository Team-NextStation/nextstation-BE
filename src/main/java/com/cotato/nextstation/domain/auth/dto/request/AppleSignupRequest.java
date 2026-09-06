package com.cotato.nextstation.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Apple 신규 회원가입 완료(약관 동의) 요청")
public record AppleSignupRequest(

        @Schema(description = "POST /apple/login 응답으로 받은 appleSignupToken", example = "eyJhbGciOiJIUzI1NiJ9...")
        @NotBlank(message = "appleSignupToken은 필수입니다.")
        String appleSignupToken,

        @Schema(description = "동의한 약관 ID 목록", example = "[1, 2]")
        @NotEmpty(message = "동의한 약관 목록은 필수입니다.")
        List<Long> agreedTermsIds,

        @Schema(description = "iOS 네이티브 Sign In with Apple SDK가 identityToken과 함께 발급한 authorizationCode. " +
                "탈퇴 시 Apple 쪽 연동을 폐기(revoke)할 수 있도록 refresh_token 교환에 사용한다.", example = "c1a2b3...")
        @NotBlank(message = "authorizationCode는 필수입니다.")
        String authorizationCode
) {
}
