package com.cotato.nextstation.domain.auth.dto.request;

import com.cotato.nextstation.domain.member.entity.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "프로필 설정 요청")
public record ProfileSetupRequest(

        @Schema(description = "닉네임. 한글/영문/숫자만 사용해 2~10자, 중복·금칙어 불가", example = "환승러")
        @NotBlank(message = "닉네임은 필수입니다.")
        String nickname,

        @Schema(description = "프로필 이미지 URL. presigned URL로 업로드 완료 후 받은 imageUrl, 또는 카카오 로그인 응답으로 받은 profileImageUrl을 그대로 전달(선택)", example = "https://bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/uuid.jpg")
        @Size(max = 1000, message = "프로필 이미지 URL은 1000자를 초과할 수 없습니다.") // Member.profileImageUrl 컬럼 길이와 동일
        String profileImageUrl,

        @Schema(description = "성별. '선택 안함'도 명시적으로 UNSPECIFIED를 보낸다", example = "MALE")
        @NotNull(message = "성별은 필수입니다.")
        Gender gender,

        @Schema(description = "생년월일 (yyyyMMdd, 선택)", example = "20010101")
        @Past(message = "생년월일은 오늘 이전 날짜여야 합니다.")
        @JsonFormat(pattern = "yyyyMMdd")
        LocalDate birthDate
) {
}