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
        List<Long> agreedTermsIds
) {
}
