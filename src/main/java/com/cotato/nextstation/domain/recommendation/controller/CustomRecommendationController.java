package com.cotato.nextstation.domain.recommendation.controller;

import com.cotato.nextstation.domain.recommendation.dto.request.CustomRecommendationRequest;
import com.cotato.nextstation.domain.recommendation.dto.response.CustomRecommendationResponse;
import com.cotato.nextstation.domain.recommendation.service.command.RecommendationCommandService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommendations")
public class CustomRecommendationController {

    private final RecommendationCommandService recommendationCommandService;

    @Operation(
            summary = "맞춤추천",
            description = """
                    출발역·이동 가능 시간·여행 스타일(최소 1개, 최대 3개)을 받아 조건에 맞는 역 하나를 추천한다.
                    - 출발역에서 이동 가능 시간 내에 닿는 뽑기 대상 역 전체를 후보로 둔다(컷 없음, 상관없음이면 시간 제한 없음).
                    - 여행 스타일 태그로 역 점수(장소 수 합 + 충족 태그 수 × 10)를 매기되, 가본 역(스탬프 있는 역)은 4점 감점한다. 동점은 역 ID 오름차순으로 정렬한다.
                    - 같은 추천 세션·같은 선택 조건에서는 아직 보여주지 않은 역을 등수 순서대로 하나씩 제공한다.
                    - 결과 화면의 다시 뽑기는 같은 recommendationSessionId를 사용하며, 새 조건 선택 흐름에서는 새 UUID를 사용한다.
                    - 해당 세션·조건의 역을 전부 제공하면 후보 전체 중 직전 맞춤추천 1건만 제외한 무작위로 전환한다.
                    - 결과 역에서 코스를 만들려면 역별 장소 목록 조회로 이어진다.

                    accessToken은 **선택**이다. 비로그인도 세션별 순차 추천을 적용하며 가본 역 감점만 적용하지 않는다.
                    보냈는데 만료·위조면 401이다.
                    """
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 — 추천 세션 ID 누락·UUID 형식 오류, 필수 조건 누락, 여행 스타일 개수·존재 여부·중복 오류 (`GlobalErrorCode.VALIDATION_ERROR`)"),
            @ApiResponse(responseCode = "401", description = "accessToken을 보냈으나 위변조 또는 만료 (`GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 출발역 또는 조건에 맞게 갈 수 있는 역이 없음 (`RecommendationErrorCode.DEPARTURE_STATION_NOT_FOUND`, `NO_REACHABLE_STATION`)"),
    })
    @PostMapping("/custom")
    public CommonResponse<CustomRecommendationResponse> recommendCustom(
            // 비로그인도 추천받을 수 있어 required = false다. 로그인 시에만 가본 역 감점이 적용된다.
            @Parameter(hidden = true) @AuthenticationPrincipal(required = false) JwtPrincipal principal,
            @Valid @RequestBody CustomRecommendationRequest request) {
        Long memberId = (principal != null) ? principal.memberId() : null;
        return CommonResponse.success(recommendationCommandService.recommendCustom(memberId, request));
    }
}
