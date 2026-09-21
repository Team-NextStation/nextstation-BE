package com.cotato.nextstation.domain.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "코스 생성 요청")
public record CourseCreateRequest(

        @Schema(description = "코스 이름 (최대 100자)", example = "보문역 코스")
        @NotBlank(message = "코스 이름은 필수입니다.")
        @Size(max = 100, message = "코스 이름은 최대 100자까지 입력할 수 있어요.")
        String name,

        @Schema(description = "역 ID", example = "1")
        @NotNull(message = "역 ID는 필수입니다.")
        Long stationId,

        @Schema(description = "선택한 장소 ID 목록 (3~10개, 중복 불가). 배열 순서대로 코스 순서가 부여된다.", example = "[1, 2, 3, 4]")
        @NotNull(message = "장소 목록은 필수입니다.")
        @Size(min = 3, max = 10, message = "장소는 3개 이상 10개 이하로 선택해야 합니다.")
        List<@NotNull Long> placeIds
) {
}
