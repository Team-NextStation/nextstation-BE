package com.cotato.nextstation.domain.course.dto.response;

import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// OS 공유 시트로 전달된 링크를 열었을 때 보여주는 화면. 소유자가 아니어도, 로그인하지 않아도 볼 수 있다.
// "내가 만든 코스 확인"(MyCourseDetailResponse)과 같은 구성이되, 그쪽은 본인 전용이라 별도로 둔다.
@Schema(description = "공유 링크로 조회하는 코스 확인 화면 응답 (지도 + 코스 순서)")
public record CourseShareResponse(

        @Schema(description = "코스 ID", example = "1")
        Long courseId,

        @Schema(description = "코스 이름", example = "민성이랑 떠나는 느좋투어")
        String name,

        @Schema(description = "코스가 속한 역 ID", example = "6")
        Long stationId,

        @Schema(description = "역 이름", example = "신림역")
        String stationName,

        @Schema(description = """
                역의 대표 호선. 화면 상단 배지 색·모양을 이 값으로 정한다.
                대표 호선이 없는 역이면 null이다.
                """, nullable = true)
        LineSummaryResponse line,

        @Schema(description = "코스에 담긴 장소 (순서대로)")
        List<CoursePlaceDetailResponse> places
) {
}
