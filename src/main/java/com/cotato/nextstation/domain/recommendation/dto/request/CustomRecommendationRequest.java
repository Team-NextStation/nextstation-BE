package com.cotato.nextstation.domain.recommendation.dto.request;

import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import com.cotato.nextstation.domain.recommendation.enums.TravelTime;
import com.cotato.nextstation.global.validation.EnumNames;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "맞춤추천 요청")
public record CustomRecommendationRequest(

        @Schema(description = "맞춤추천 결과 화면 세션 ID. 같은 결과 화면에서 다시 뽑을 때 동일한 값을 사용하며 이력은 최근 24시간만 반영한다.",
                example = "550e8400-e29b-41d4-a716-446655440000")
        @NotBlank(message = "추천 세션 ID는 필수입니다.")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "추천 세션 ID는 UUID 형식이어야 합니다."
        )
        String recommendationSessionId,

        @Schema(description = "출발역 ID (저장된 출발역 또는 검색으로 고른 역)", example = "1")
        @NotNull(message = "출발역은 필수입니다.")
        Long departureStationId,

        @Schema(description = "이동 가능 시간", example = "THIRTY_MINUTES",
                allowableValues = {"THIRTY_MINUTES", "ONE_HOUR", "ANY"})
        @NotNull(message = "이동 가능 시간은 필수입니다.")
        TravelTime travelTime,

        @Schema(description = "여행 스타일 태그 (최소 1개, 최대 3개, 중복 불가)", example = "[\"NATURE\", \"BUDGET\", \"EXPERIENCE\"]")
        @NotNull(message = "여행 스타일은 필수입니다.")
        @Size(min = 1, max = 3, message = "여행 스타일은 1개 이상 3개 이하로 선택해야 합니다.")
        @EnumNames(value = PlaceTagName.class, message = "존재하지 않는 여행 스타일입니다.")
        List<String> travelStyles
) {
}
