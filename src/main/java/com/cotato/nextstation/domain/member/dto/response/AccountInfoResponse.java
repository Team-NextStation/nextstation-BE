package com.cotato.nextstation.domain.member.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "계정 정보 조회 응답")
public record AccountInfoResponse(

        @Schema(description = "가입 경로. 소셜 연동이 없으면 LOCAL", example = "KAKAO", allowableValues = {"LOCAL", "KAKAO", "APPLE"})
        String provider,

        @Schema(description = "가입한 이메일. 카카오 계정에 인증된 이메일이 없는 회원은 null", example = "user@example.com")
        String email,

        @Schema(type = "string", pattern = "^[0-9]{8}$", description = "생년월일 (yyyyMMdd)", example = "\"20010101\"")
        @JsonFormat(pattern = "yyyyMMdd")
        LocalDate birthDate
) {
}
