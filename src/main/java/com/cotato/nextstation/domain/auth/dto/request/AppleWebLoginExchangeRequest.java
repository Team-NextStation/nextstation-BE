package com.cotato.nextstation.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "웹 Apple 로그인 콜백 리다이렉트로 받은 1회용 code 교환 요청")
public record AppleWebLoginExchangeRequest(

        @Schema(description = "콜백 리다이렉트 쿼리 파라미터로 받은 1회용 code", example = "AbCdEf123...")
        @NotBlank(message = "code는 필수입니다.")
        String code
) {
}
