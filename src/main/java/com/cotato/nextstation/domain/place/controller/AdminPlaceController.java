package com.cotato.nextstation.domain.place.controller;

import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCardResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceDetailResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceListResponse;
import com.cotato.nextstation.domain.place.enums.CategoryCode;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.service.query.AdminPlaceQueryService;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.security.AuthenticationPrincipal;
import com.cotato.nextstation.global.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Place")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/admin/places")
public class AdminPlaceController {

    private final AdminPlaceQueryService adminPlaceQueryService;

    @Operation(
            summary = "관리자 장소 목록 조회",
            description = """
                    모든 등록 상태의 장소를 관리자용 카드로 조회한다.
                    - `lineId`는 대표 노선을 기준으로 한다.
                    - 필터를 여러 개 보내면 모든 조건을 만족하는 장소만 반환한다.
                    - 장소명 오름차순이며 `nextCursor`를 다음 요청의 `cursor`로 그대로 보낸다.
                    - `availableLines`와 `availableStations`는 첫 페이지에서만 제공한다.
                    - 대표 사진은 실제 장소 사진의 첫 장이며, 없을 때는 null을 반환한다.
                    """)
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (결과가 없으면 빈 목록)"),
            @ApiResponse(responseCode = "400", description = "잘못된 필터·커서·페이지 크기"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping
    public CommonResponse<AdminPlaceListResponse> getPlaces(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "대표 호선 ID") @RequestParam(required = false) @Positive Long lineId,
            @Parameter(description = "역 ID") @RequestParam(required = false) @Positive Long stationId,
            @Parameter(description = "카테고리 코드") @RequestParam(required = false) CategoryCode categoryCode,
            @Parameter(description = "장소 등록 상태") @RequestParam(required = false) PlaceStatus status,
            @Parameter(description = "다음 페이지 커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기 (1~50, 기본 10)") @RequestParam(required = false) Integer size) {
        return CommonResponse.success(adminPlaceQueryService.getPlaces(
                principal.memberId(), lineId, stationId, categoryCode, status, cursor, size));
    }

    @Operation(
            summary = "관리자 장소명 검색",
            description = """
                    필터 목록과 별도로 장소명만 검색한다.
                    - 검색어가 없거나 공백이면 빈 목록을 반환한다.
                    - 완전일치, 접두사일치, 포함일치 순으로 최대 20개를 반환한다.
                    - 모든 등록 상태가 검색 대상이다.
                    """)
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공 (결과가 없으면 빈 목록)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping("/search")
    public CommonResponse<List<AdminPlaceCardResponse>> searchPlaces(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "검색할 장소명") @RequestParam(required = false) String keyword) {
        return CommonResponse.success(adminPlaceQueryService.searchPlaces(principal.memberId(), keyword));
    }

    @Operation(
            summary = "관리자 장소 상세 조회",
            description = """
                    APPROVED, PENDING, REJECTED, DELETED 상태를 구분하지 않고 장소 상세를 조회한다.
                    실제 장소 사진을 노출 순서대로 제공하며, 삭제·반려 장소는 각각의 사유를 함께 반환한다.
                    """)
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @ApiResponse(responseCode = "404", description = "장소 없음 (`PlaceErrorCode.PLACE_NOT_FOUND`)")
    })
    @GetMapping("/{placeId}")
    public CommonResponse<AdminPlaceDetailResponse> getPlaceDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "장소 ID") @PathVariable @Positive Long placeId) {
        return CommonResponse.success(adminPlaceQueryService.getPlaceDetail(principal.memberId(), placeId));
    }
}
