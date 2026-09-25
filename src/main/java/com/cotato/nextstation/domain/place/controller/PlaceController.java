package com.cotato.nextstation.domain.place.controller;

import com.cotato.nextstation.domain.place.dto.response.PlaceDetailResponse;
import com.cotato.nextstation.domain.place.dto.response.PlaceReviewListResponse;
import com.cotato.nextstation.domain.place.enums.PlaceReviewSortType;
import com.cotato.nextstation.domain.place.service.query.PlaceQueryService;
import com.cotato.nextstation.domain.place.service.query.PlaceReviewQueryService;
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
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PlaceQueryService placeQueryService;
    private final PlaceReviewQueryService placeReviewQueryService;

    @Operation(
            summary = "장소 상세 조회",
            description = """
                    장소 기본 정보, 이미지, 공개 리뷰 미리보기(최신순 최대 3개)를 함께 조회한다.
                    - 등록된 이미지가 없으면 카테고리 기본 이미지 1장으로 대체된다.
                    - 비로그인도 조회할 수 있다. 로그인 시에만 차단한 사용자의 리뷰가 미리보기에서 제외된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "accessToken을 보냈으나 위변조 또는 만료 (`GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 장소 (`PlaceErrorCode.PLACE_NOT_FOUND`)"),
    })
    @GetMapping("/{placeId}")
    public CommonResponse<PlaceDetailResponse> getPlaceDetail(
            @PathVariable Long placeId,
            @Parameter(hidden = true) @AuthenticationPrincipal(required = false) JwtPrincipal principal) {
        Long memberId = (principal != null) ? principal.memberId() : null;
        return CommonResponse.success(placeQueryService.getPlaceDetail(placeId, memberId));
    }

    @Operation(
            summary = "장소 리뷰 목록 조회",
            description = """
                장소에 달린 리뷰 목록을 조회한다.
                - 정렬: 추천순(likeCount 내림차순, 기본) / 최신순
                - cursor 기반 페이지네이션
                - totalCount는 최초 조회(cursor 없음) 시에만 내려주며, 다음 페이지 요청 시 null이다
                - 비로그인 사용자도 조회 가능 (accessToken 생략 시 isLiked는 항상 false)

                accessToken은 **선택**이다. 없으면 비로그인 조회로 동작하고,
                보냈는데 만료·위조면 401이다.
                """
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "accessToken을 보냈으나 위변조 또는 만료 (`GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 장소 (`PlaceErrorCode.PLACE_NOT_FOUND`)"),
    })
    @GetMapping("/{placeId}/reviews")
    public CommonResponse<PlaceReviewListResponse> getReviews(
            @Parameter(description = "장소 ID", example = "10")
            @PathVariable Long placeId,
            @Parameter(description = "정렬 기준", example = "RECOMMEND")
            @RequestParam(defaultValue = "RECOMMEND") PlaceReviewSortType sort,
            @Parameter(description = "다음 페이지 커서")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기", example = "10")
            @RequestParam(required = false) Integer size,
            // 비로그인도 조회할 수 있어야 해서 required = false다. 로그인 시에만 isLiked를 채운다.
            @Parameter(hidden = true) @AuthenticationPrincipal(required = false) JwtPrincipal principal) {
        Long memberId = (principal != null) ? principal.memberId() : null;
        return CommonResponse.success(placeReviewQueryService.getReviews(placeId, sort, cursor, size, memberId));
    }

}