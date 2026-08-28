package com.cotato.nextstation.domain.recommendation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "랜덤추천 요청")
public record RandomRecommendationRequest(

        @Schema(description = "랜덤추천 결과 화면 세션 ID. 같은 결과 화면에서 다시 뽑을 때 동일한 값을 사용한다.",
                example = "550e8400-e29b-41d4-a716-446655440000")
        @NotBlank(message = "추천 세션 ID는 필수입니다.")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "추천 세션 ID는 UUID 형식이어야 합니다."
        )
        String recommendationSessionId
) {
}
