package com.cotato.nextstation.domain.course.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "내 코스로 만들기 요청")
public record CourseCopyRequest(

        @Schema(description = """
                복사한 코스에 부여할 이름 (최대 100자, 필수).
                사용자가 이름을 고치지 않았으면 화면에 채워둔 원본 이름을 그대로 실어 보낸다.
                서버는 받은 값을 그대로 저장하며, 생략하면 원본 이름으로 대체되지 않고 400이 응답된다.
                """, example = "민성이랑 떠나는 느좋투어")
        @NotBlank(message = "코스 이름은 필수입니다.")
        @Size(max = 100, message = "코스 이름은 최대 100자까지 입력할 수 있어요.")
        String name,

        @Schema(description = """
                순서를 바꿔 저장할 때 사용하는 장소 ID 목록. 배열 순서대로 코스 순서가 부여된다.
                원본 코스의 장소 구성과 정확히 일치해야 하며(장소를 빼거나 더할 수 없다),
                생략하면 원본 순서 그대로 복사된다.
                """, example = "[3, 1, 4, 2]", nullable = true)
        List<@NotNull Long> placeIds
) {
}
