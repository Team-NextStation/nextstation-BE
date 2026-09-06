package com.cotato.nextstation.domain.place.controller;

import com.cotato.nextstation.domain.place.dto.request.AdminPlaceCreateRequest;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCreateResponse;
import com.cotato.nextstation.domain.place.dto.response.KakaoPlaceSearchResponse;
import com.cotato.nextstation.domain.place.service.command.AdminPlaceCommandService;
import com.cotato.nextstation.domain.place.service.query.KakaoPlaceSearchService;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.security.AuthenticationPrincipal;
import com.cotato.nextstation.global.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/places")
public class AdminPlaceController {

    private final KakaoPlaceSearchService kakaoPlaceSearchService;
    private final AdminPlaceCommandService adminPlaceCommandService;

    @Operation(
            summary = "[관리자] 카카오 장소 검색",
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
            summary = "[관리자] 장소 등록",
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
}
