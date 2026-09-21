package com.cotato.nextstation.domain.course.controller;

import com.cotato.nextstation.domain.course.dto.request.ExploreCourseCondition;
import com.cotato.nextstation.domain.course.dto.response.ExploreCourseListResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreResponse;
import com.cotato.nextstation.domain.course.entity.CourseSort;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.domain.course.service.query.ExploreQueryService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 둘러보기 화면이 쓰는 조회 API를 모은다. 데이터는 코스라 Course 도메인이 소유한다.
@Tag(name = "Explore")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/explore")
public class ExploreController {

    private final ExploreQueryService exploreQueryService;
    private final CourseQueryService courseQueryService;

    @Operation(
            summary = "둘러보기 메인 조회",
            description = """
                    둘러보기 탭 첫 화면이다. 세 섹션을 한 번에 내려준다.
                    - `popularCourses`: 사람들이 많이 찾는 코스 **6개** (좋아요 수 순)
                    - `conceptTours`: 컨셉별 투어 **3개** (표시 순서대로)
                    - `lines` + `selectedLineId` + `lineCourses`: 노선 칩과 처음 선택된 노선의 코스 **3개**

                    더보기나 칩 전환부터는 개별 API를 쓴다.
                    - 많이 찾는 코스 더보기 → `GET /api/v1/explore/courses/popular`
                    - 컨셉별 투어 더보기 → `GET /api/v1/explore/concept-tours`
                    - 노선 칩 전환 → `GET /api/v1/explore/courses?lineId=`

                    `lines`에는 뽑기 역이 속한 노선 중 **화면에 칩이 있는 1~9호선만** 담는다.
                    공개 코스가 없는 노선은 목록에서 빼지 않고 `hasCourses = false`로 내려주므로,
                    프론트는 **비활성 칩으로 그린다**. 코스가 쌓일 때마다 칩이 늘어나 노선도가 흔들리는 것을 막기 위한 것이다.
                    `id`는 그대로 `lineId` 파라미터에 넣어 요청하면 된다. 시딩 순서로 정해지는 값이라 **하드코딩하지 않는다.**
                    뽑기 역 중 환승역이 경의중앙선·우이신설선에도 속하지만 그 두 노선은 칩이 없어 목록에서 뺀다.
                    코스는 함께 속한 1~9호선 칩에서 그대로 조회된다(예: 옥수역 코스는 3호선 칩에서 나온다).
                    `selectedLineId`는 `hasCourses = true`인 첫 노선이라 진입 화면이 비지 않는다.

                    accessToken은 **선택**이다. 비로그인도 조회할 수 있고 카드의 `isLiked`는 항상 false다.
                    토큰을 보냈는데 만료되었거나 위변조된 경우는 401이다.
                    """
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (공개 코스가 없으면 각 목록이 빈 배열)"),
            @ApiResponse(responseCode = "401", description = "accessToken을 보냈으나 위변조 또는 만료 (`GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
    })
    @GetMapping
    public CommonResponse<ExploreResponse> getExplore(
            @Parameter(hidden = true) @AuthenticationPrincipal(required = false) JwtPrincipal principal) {
        Long memberId = principal != null ? principal.memberId() : null;
        return CommonResponse.success(exploreQueryService.getExplore(memberId));
    }

    @Operation(
            summary = "노선따라 둘러보기 + 코스 검색",
            description = """
                    둘러보기의 노선따라 둘러보기와 코스 검색이 이 API를 함께 쓴다.
                    - 공개된 여행일지가 있는 코스만 나온다.
                    - 카드를 누르면 `journalId`로 여행일지 상세를 연다. 목록에 그 값이 함께 내려간다.
                    - `lineId`/`stationId`/`keyword`는 모두 선택 사항이며, 함께 주면 전부 만족하는 코스만 나온다.
                    - `lineId` 필터는 역이 **속한 호선 전체**를 기준으로 한다. 환승역 코스는 소속된 모든 호선에서 조회된다.
                    - `availableStations`는 "역 선택" 드롭다운을 그리는 데 쓴다. 코스가 붙을 수 있는 역을 전부 담고,
                      공개 코스가 없는 역은 `hasCourses = false`로 내려주므로 **비활성으로 그린다**(노선 칩과 같은 방식).
                      `lineId`만 반영하고 `stationId`·`keyword`는 반영하지 않는다(고른 역으로 좁히면 그 역만 남는다).
                      **첫 페이지에서만** 채워지고 다음 페이지부터는 빈 배열이다.
                    - `keyword`는 **코스 이름과 역명**만 검색한다. 역명은 꼬리의 `역`을 떼고 비교하므로
                      `신림`과 `신림역` 중 무엇을 넣어도 결과가 같다.
                    - `sort`는 `POPULAR`(기본, 조회수 + 좋아요×2, 동률이면 최신순) 또는 `LATEST`(최신순)다.
                      정렬 토글이 없는 검색 결과 화면은 생략하면 되고, 그때도 인기순으로 나온다.
                    - **정렬을 바꾸면 커서를 버리고 첫 페이지부터 다시 요청해야 한다.** 정렬마다 커서 구조가 달라
                      이전 커서를 그대로 보내면 400이다.
                    - `isLiked`는 요청한 회원의 좋아요 여부다. 비로그인이면 항상 false다.
                    - `imageUrl`은 작성자가 여행일지에 올린 첫 사진이다. 아직 사진 데이터가 없어 현재는 null이다.
                    """
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (결과 없으면 빈 목록)"),
            @ApiResponse(responseCode = "400", description = "size 범위를 벗어남 (`GlobalErrorCode.INVALID_PAGE_SIZE`) 또는 커서가 잘못됨/정렬과 맞지 않음 (`GlobalErrorCode.INVALID_CURSOR`)"),
            @ApiResponse(responseCode = "401", description = "accessToken을 보냈으나 위변조 또는 만료 (`GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
    })
    @GetMapping("/courses")
    public CommonResponse<ExploreCourseListResponse> getExploreCourses(
            @Parameter(hidden = true) @AuthenticationPrincipal(required = false) JwtPrincipal principal,
            @Parameter(description = "호선 필터 (생략하면 전체)", example = "2")
            @RequestParam(required = false) Long lineId,
            @Parameter(description = "역 필터 (생략하면 전체)", example = "123")
            @RequestParam(required = false) Long stationId,
            @Parameter(description = "검색어 (코스 이름·역명)", example = "신림")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "정렬 기준 (기본 POPULAR)", example = "POPULAR")
            @RequestParam(required = false) CourseSort sort,
            @Parameter(description = "다음 페이지 커서 (첫 페이지는 생략)")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (1~50, 기본 10)", example = "10")
            @RequestParam(required = false) Integer size) {
        Long memberId = principal != null ? principal.memberId() : null;
        ExploreCourseCondition condition = new ExploreCourseCondition(lineId, stationId, keyword, null);
        return CommonResponse.success(
                courseQueryService.getExploreCourses(memberId, condition, sort, cursor, size));
    }

    @Operation(
            summary = "사람들이 많이 찾는 코스 조회",
            description = """
                    둘러보기의 "사람들이 많이 찾는 코스"다. 좋아요 수 내림차순, 동률이면 조회수 내림차순, 그마저 동률이면 최신순이다.
                    - **상위 30개까지만** 보여준다. 30번째를 넘어가면 `hasNext`가 false다.
                    - 둘러보기 목록(`GET /api/v1/explore/courses`)의 `sort=POPULAR`(조회수 + 좋아요×2)와는
                      **다른 기준**이다. 여기는 담은 횟수(좋아요 수)만 본다.
                    - 순위 번호는 내려주지 않는다. 정렬된 순서 그대로 프론트에서 매기면 된다.
                    - `cursor`에는 다음 시작 위치가 담긴다. 그대로 다음 요청에 넣으면 된다.
                    - 공개된 여행일지가 있는 코스만 나오며, 카드를 누르면 `journalId`로 여행일지 상세를 연다.
                    """
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (결과 없으면 빈 목록)"),
            @ApiResponse(responseCode = "400", description = "size 범위를 벗어남 (`GlobalErrorCode.INVALID_PAGE_SIZE`) 또는 커서가 잘못됨 (`GlobalErrorCode.INVALID_CURSOR`)"),
            @ApiResponse(responseCode = "401", description = "accessToken을 보냈으나 위변조 또는 만료 (`GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
    })
    @GetMapping("/courses/popular")
    public CommonResponse<ExploreCourseListResponse> getMostLikedCourses(
            @Parameter(hidden = true) @AuthenticationPrincipal(required = false) JwtPrincipal principal,
            @Parameter(description = "다음 페이지 커서 (첫 페이지는 생략)")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (1~50, 기본 10)", example = "10")
            @RequestParam(required = false) Integer size) {
        Long memberId = principal != null ? principal.memberId() : null;
        return CommonResponse.success(courseQueryService.getMostLikedCourses(memberId, cursor, size));
    }
}
