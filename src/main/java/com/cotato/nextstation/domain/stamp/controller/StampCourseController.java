package com.cotato.nextstation.domain.stamp.controller;

import com.cotato.nextstation.domain.stamp.dto.response.CourseCompleteResponse;
import com.cotato.nextstation.domain.stamp.dto.response.MyStampDetailResponse;
import com.cotato.nextstation.domain.stamp.dto.response.MyStampListResponse;
import com.cotato.nextstation.domain.stamp.dto.response.StationPopularCoursesResponse;
import com.cotato.nextstation.domain.stamp.service.command.StampCommandService;
import com.cotato.nextstation.domain.stamp.service.query.MemberStampQueryService;
import com.cotato.nextstation.domain.stamp.service.query.StampCourseQueryService;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.security.AuthenticationPrincipal;
import com.cotato.nextstation.global.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class StampCourseController {

    private final StampCommandService stampCommandService;
    private final StampCourseQueryService stampCourseQueryService;
    private final MemberStampQueryService memberStampQueryService;


    @Operation(
            summary = "코스 완료 처리",
            description = """
                    코스를 완료 처리하고 스탬프를 획득한다.
                    - 본인이 만든 코스만 완료할 수 있다.
                    - 같은 코스를 여러 번 완료할 수 없다.
                    - 완료 시 발급된 memberStampId는 여행일지 작성 시 사용한다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "완료 성공"),
            @ApiResponse(responseCode = "403", description = "본인 코스가 아님 (`StampErrorCode.COURSE_NOT_OWNED`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 코스 (`CourseErrorCode.COURSE_NOT_FOUND`)"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @PostMapping("/courses/{courseId}/complete")
    public CommonResponse<CourseCompleteResponse> completeCourse(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "코스 ID", example = "1")
            @PathVariable Long courseId) {
        return CommonResponse.success(stampCommandService.completeCourse(principal.memberId(), courseId));
    }


    @Operation(
            summary = "역별 인기코스 조회",
            description = """
                    스탬프 페이지 내에서 특정 역의 인기 코스 상위 3개를 조회한다.
                    - 인기순: 조회수 + 저장수×2 내림차순
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증이 필요함"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @GetMapping("/stamps/stations/{stationId}/courses")
    public CommonResponse<StationPopularCoursesResponse> getPopularCoursesByStation(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "역 ID", example = "12")
            @PathVariable Long stationId) {
        return CommonResponse.success(
                stampCourseQueryService.getPopularCoursesByStation(principal.memberId(), stationId));
    }


    @Operation(
            summary = "내 스탬프 목록 조회",
            description = """
                    내가 완료한 코스의 스탬프를 역 기준으로 조회한다.
                    - 같은 역에서 여러 코스를 완료해도 스탬프는 하나만 노출된다 (최초 획득 기준).
                    - 1호선 → 9호선 순으로 정렬되며, 대표 호선이 없는 역은 맨 뒤에 온다. 동일 호선 내에서는 역명 가나다순으로 정렬된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증이 필요함"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @GetMapping("/stamps")
    public CommonResponse<MyStampListResponse> getMyStamps(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal) {
        return CommonResponse.success(memberStampQueryService.getMyStamps(principal.memberId()));
    }


    @Operation(
            summary = "내 스탬프 상세 조회",
            description = """
                    특정 역에 대해 내가 최초로 완료한 스탬프의 상세 정보를 조회한다.
                    - 같은 역에서 여러 코스를 완료했어도 역/노선/획득일은 최초 획득 스탬프 기준으로 조회한다.
                    - journalId는 최초 획득 스탬프에 연결된 일지로 한정하지 않고, 해당 역에서 내가 작성한
                      여행일지 중 완주 시점이 가장 이른 것의 id를 반환한다. 그런 일지가 하나도 없으면 null이다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증이 필요함"),
            @ApiResponse(responseCode = "404", description = "해당 역에 대한 스탬프가 없음 (`StampErrorCode.MEMBER_STAMP_NOT_FOUND`)"),
    })
    @SecurityRequirement(name = "accessTokenAuth")
    @GetMapping("/stamps/{stationId}")
    public CommonResponse<MyStampDetailResponse> getMyStampDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "역 ID", example = "5")
            @PathVariable Long stationId) {
        return CommonResponse.success(memberStampQueryService.getMyStampDetail(principal.memberId(), stationId));
    }

}