package com.cotato.nextstation.domain.place.controller;

import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCardResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceDetailResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceListResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceTagResponse;
import com.cotato.nextstation.domain.place.enums.CategoryCode;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.service.query.AdminPlaceQueryService;
import com.cotato.nextstation.domain.place.dto.request.AdminPlaceCreateRequest;
import com.cotato.nextstation.domain.place.dto.request.AdminPlaceUpdateRequest;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCreateResponse;
import com.cotato.nextstation.domain.place.dto.response.KakaoPlaceSearchResponse;
import com.cotato.nextstation.domain.place.service.command.AdminPlaceCommandService;
import com.cotato.nextstation.domain.place.service.query.KakaoPlaceSearchService;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.security.AuthenticationPrincipal;
import com.cotato.nextstation.global.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final KakaoPlaceSearchService kakaoPlaceSearchService;
    private final AdminPlaceCommandService adminPlaceCommandService;

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
            @Parameter(description = "페이지 크기 (1~50, 기본 10)")
            @RequestParam(required = false) @Min(1) @Max(50) Integer size) {
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
            summary = "관리자 장소 활성 태그 선택지 조회",
            description = "기존 장소 수정 화면에서 선택할 수 있는 활성 태그의 코드와 표시명을 조회한다."
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/CommonResponseListAdminPlaceTagResponse"))),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping("/tags")
    public CommonResponse<List<AdminPlaceTagResponse>> getActiveTags(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal) {
        return CommonResponse.success(adminPlaceQueryService.getActiveTags(principal.memberId()));
    }

    @Operation(
            summary = "관리자 장소 상세 조회",
            description = """
                    APPROVED, PENDING, REJECTED, DELETED 상태를 구분하지 않고 장소 상세를 조회한다.
                    - 장소명, 대표 노선, 역명, 등록 상태, 카테고리, 해시태그, 한 줄 설명을 제공한다.
                    - 주소, X 좌표(경도), Y 좌표(위도), 카카오맵 URL은 읽기 전용 정보로 제공한다.
                    - 실제 장소 사진을 노출 순서대로 제공한다.
                    - 삭제·반려 장소는 각각의 사유를 함께 반환한다.
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

    @Operation(
            summary = "관리자 카카오 장소 검색",
            description = """
                    카카오 로컬 API로 장소 등록 후보를 검색한다. 관리자(ADMIN, ACTIVE)만 호출할 수 있다.

                    파라미터는 화면에 있는 값을 그대로 보내면 된다. 서버가 아래 규칙으로 검색어를 만든다.

                    - `address` 있음: 이 값으로만 검색하고 `stationId`·`keyword`는 무시한다.
                    - `address` 없고 `stationId` 있음: `"{역명} {keyword}"`로 검색, 0건이면 `"{keyword}"`로 재검색.
                    - `address` 없고 `stationId` 없음: `"{keyword}"`로 검색.
                    - `keyword`와 `address`가 모두 비어 있으면 400.

                    응답은 주소가 아니라 장소 후보이며, 정확도순 상위 10건이다. 결과가 없으면 빈 배열(200)이다.
                    각 후보는 등록에 필요한 값을 모두 담고 있어 그대로 장소 등록 API에 실어 보내면 된다.
                    """
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "keyword와 address가 모두 비어 있음 (`GlobalErrorCode.INVALID_REQUEST`)"),
            @ApiResponse(responseCode = "401", description = "accessToken 누락, 위변조 또는 만료 (`GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
            @ApiResponse(responseCode = "403", description = "관리자가 아님 (`GlobalErrorCode.FORBIDDEN`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 역 (`StationErrorCode.STATION_NOT_FOUND`)"),
            @ApiResponse(responseCode = "502", description = "카카오 API 통신 실패 (`GlobalErrorCode.EXTERNAL_API_ERROR`)"),
    })
    @GetMapping("/kakao-search")
    public CommonResponse<KakaoPlaceSearchResponse> searchKakaoPlaces(
            @Parameter(description = "역 ID. 넣으면 역명을 붙여 검색한다. address가 있으면 무시된다", example = "12")
            @RequestParam(required = false) Long stationId,
            @Parameter(description = "장소명 검색어. address가 있으면 무시된다", example = "크래커")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "주소. 넣으면 stationId·keyword를 무시하고 이 값으로만 검색한다",
                    example = "서울 용산구 원효로1가 48")
            @RequestParam(required = false) String address,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal) {
        return CommonResponse.success(
                kakaoPlaceSearchService.searchKakaoPlaces(principal.memberId(), stationId, keyword, address));
    }

    @Operation(
            summary = "관리자 장소 등록",
            description = """
                    카카오 검색으로 고른 장소를 등록한다. 관리자(ADMIN, ACTIVE)만 호출할 수 있다.

                    - `kakaoPlaceId`·`placeName`·`address`·`contactNumber`·`xCoordinate`·`yCoordinate`는
                      검색 응답 값을 그대로 실어 보낸다. 카카오에 place id 단건 조회 API가 없어 서버가 다시 가져올 수 없다.
                    - 상태는 `PENDING`으로 저장되며, 검수를 거쳐 `APPROVED`가 되기 전에는 서비스 조회에 노출되지 않는다.
                    - 중복 판정 기준은 `(역, 카카오 장소 ID)`다. 같은 장소라도 역이 다르면 별도 장소로 등록된다.
                    - 사진은 선택값이다. presigned URL 발급(`folder=STATIC_PLACE`)으로 S3 업로드를 마친 뒤 
                      받은 `imageUrl`을 순서대로 넣는다. 첫 번째가 대표 이미지이며, 비우면 카테고리 기본 이미지가 나간다.
                    """
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 장소 사진 경로가 아닌 이미지 URL (`GlobalErrorCode.VALIDATION_ERROR`, `ImageErrorCode.INVALID_IMAGE_URL_FORMAT`)"),
            @ApiResponse(responseCode = "401", description = "accessToken 누락, 위변조 또는 만료 (`GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
            @ApiResponse(responseCode = "403", description = "관리자가 아님 (`GlobalErrorCode.FORBIDDEN`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 역 (`StationErrorCode.STATION_NOT_FOUND`)"),
            @ApiResponse(responseCode = "409", description = "해당 역에 이미 등록된 장소 (`PlaceErrorCode.PLACE_ALREADY_REGISTERED`)"),
    })
    @PostMapping
    public CommonResponse<AdminPlaceCreateResponse> createPlace(
            @Valid @RequestBody AdminPlaceCreateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal) {
        return CommonResponse.success(adminPlaceCommandService.createPlace(principal.memberId(), request));
    }

    @Operation(
            summary = "관리자 기존 장소 수정",
            description = """
                    APPROVED 또는 PENDING 상태인 장소의 태그·한 줄 설명·사진을 부분 수정한다.
                    - 요청에서 생략한 필드는 기존 값을 유지한다.
                    - `tagNames`는 수정 후 적용할 서로 다른 활성 태그 2개 전체를 보낸다.
                    - `imageUrls`는 새로 추가할 사진이며, `deleteImageIds`는 삭제할 기존 사진 ID다.
                    - 사진 삭제와 추가를 반영한 뒤 남은 사진 순서를 0부터 다시 정렬한다.
                    """
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/CommonResponseAdminPlaceDetailResponse"))),
            @ApiResponse(responseCode = "400", description = "요청값 검증 실패, 잘못된 사진 또는 이미지 URL"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @ApiResponse(responseCode = "404", description = "장소 없음 (`PlaceErrorCode.PLACE_NOT_FOUND`)"),
            @ApiResponse(responseCode = "409", description = "수정 불가능한 장소 상태 (`PlaceErrorCode.PLACE_NOT_EDITABLE`)")
    })
    @PatchMapping("/{placeId}")
    public CommonResponse<AdminPlaceDetailResponse> updatePlace(
            @Parameter(description = "장소 ID") @PathVariable @Positive Long placeId,
            @Valid @RequestBody AdminPlaceUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal) {
        return CommonResponse.success(
                adminPlaceCommandService.updatePlace(principal.memberId(), placeId, request));
    }
}
