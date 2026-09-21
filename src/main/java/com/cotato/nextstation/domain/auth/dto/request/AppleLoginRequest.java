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
        String nonce,

        @Schema(description = "iOS 네이티브 Sign In with Apple SDK가 identityToken과 함께 발급한 authorizationCode. " +
                "resultType=NEW_MEMBER일 때만 의미가 있다 - 탈퇴 시 Apple 쪽 연동을 폐기(revoke)할 수 있도록 이 시점에 " +
                "미리 refresh_token으로 교환해둔다. 생략해도 로그인 판별 자체는 정상 동작하며, 이 경우 나중에 " +
                "탈퇴해도 Apple 쪽 연동이 자동 해제되지 않을 뿐이다.", example = "c1a2b3...")
        String authorizationCode
) {
}
