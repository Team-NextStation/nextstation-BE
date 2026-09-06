package com.cotato.nextstation.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Apple 로그인/신규가입 판별 요청")
public record AppleLoginRequest(

        @Schema(description = "iOS 네이티브 Sign In with Apple SDK가 발급한 identity token(JWT)", example = "eyJraWQiOiJ...")
        @NotBlank(message = "identityToken은 필수입니다.")
        String identityToken,

        @Schema(description = "클라이언트가 ASAuthorizationAppleIDRequest.nonce에 사용한 원문(raw) 값. " +
                "SHA-256 해싱한 값이 identityToken의 nonce 클레임과 일치해야 한다 - 탈취된 identity token 재전송 방지용", example = "a1b2c3...")
        @NotBlank(message = "nonce는 필수입니다.")
        String nonce
) {
}
