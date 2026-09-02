package com.cotato.nextstation.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Apple 로그인/신규가입 판별 요청")
public record AppleLoginRequest(

        @Schema(description = "iOS 네이티브 Sign In with Apple SDK가 발급한 identity token(JWT)", example = "eyJraWQiOiJ...")
        @NotBlank(message = "identityToken은 필수입니다.")
        String identityToken
) {
}
