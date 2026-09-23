package com.cotato.nextstation.domain.course.controller;

import com.cotato.nextstation.domain.course.dto.response.PlaceCourseResponse;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.security.AuthenticationPrincipal;
import com.cotato.nextstation.global.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 경로는 /places 하위지만 course 데이터를 다루므로 Course 도메인이 소유한다.
// 장소 상세 응답(Part3)에 코스를 끼워 넣는 대신 별도로 열어, 두 도메인이 서로를 참조하지 않게 한다.
@Tag(name = "Course")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/places")
public class PlaceCourseController {

    private final CourseQueryService courseQueryService;

    @Operation(
            summary = "장소를 포함한 코스 조회",
            description = """
                    장소 상세 화면 하단의 "이 장소를 포함한 코스" 가로 스크롤 목록이다.
                    - 장소 상세 화면 자체는 비로그인도 볼 수 있지만, 이 목록은 **로그인 필수**다.
                    - 공개된 여행일지가 있는 코스만 노출한다.
                    - **인기순 상위 6개 고정**이며, 노출 순서는 매번 섞인다. 더보기 화면이 없어 페이징하지 않는다.
                    - 코스가 없으면 빈 배열이다. 없는 장소를 조회해도 빈 배열이다.
                    - `travelDuration`은 여행기록의 "코스 시간" 선택지와 같은 값(`SHORT`/`HALF_DAY`/`FULL_DAY`)이다.
                      여행일지에 소요시간이 기록되기 전까지는 장소 수로 추정한 값이 나간다.
                    """
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (코스가 없으면 빈 배열)"),
            @ApiResponse(responseCode = "401", description = "accessToken 누락, 위변조, 또는 만료 (`GlobalErrorCode.UNAUTHORIZED`, `GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
    })
    @GetMapping("/{placeId}/courses")
    public CommonResponse<List<PlaceCourseResponse>> getCoursesByPlace(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "장소 ID", example = "1")
            @PathVariable Long placeId) {
        return CommonResponse.success(courseQueryService.getCoursesByPlace(principal.memberId(), placeId));
    }
}
