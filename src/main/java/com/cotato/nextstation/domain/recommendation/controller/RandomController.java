package com.cotato.nextstation.domain.recommendation.controller;

import com.cotato.nextstation.domain.recommendation.dto.request.RandomRecommendationRequest;
import com.cotato.nextstation.domain.recommendation.dto.response.CoursePreviewResponse;
import com.cotato.nextstation.domain.recommendation.dto.response.RandomRecommendationResponse;
import com.cotato.nextstation.domain.recommendation.service.command.RecommendationCommandService;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.security.AuthenticationPrincipal;
import com.cotato.nextstation.global.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/random")
public class RandomController {

    private final RecommendationCommandService recommendationCommandService;

    @Operation(
            summary = "랜덤뽑기",
            description = """
                    뽑기 대상 역 중 무작위로 1개를 뽑아 역 정보와 코스 미리보기를 반환한다.
                    - 같은 추천 세션에서는 이미 보여준 역을 제외하고 무작위로 추천한다.
                    - 결과 화면에서 다시 뽑을 때는 같은 recommendationSessionId를 사용한다.
                    - 새 결과 화면에 진입할 때는 새로운 UUID를 사용한다.
                    - 최근 24시간의 세션 이력만 반영하며, 그보다 오래된 세션 ID는 새 세션처럼 처리한다.
                    - 해당 세션에서 모든 역을 추천하면 직전 추천역만 제외하고 무작위로 추천한다. 단, 후보가 1개뿐이면 같은 역을 다시 추천한다.
                    - 환승역이면 소속 노선을 모두 반환한다.
                    - 코스 미리보기는 카테고리별 장소 1개씩으로 구성하며 장소가 없는 카테고리는 제외한다.

                    accessToken은 선택이다. 비로그인도 세션별 중복 방지를 적용한다.
                    보냈는데 만료·위조된 토큰이면 401이다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "뽑기 성공"),
            @ApiResponse(responseCode = "400", description = "추천 세션 ID 누락 또는 UUID 형식 오류"),
            @ApiResponse(responseCode = "401", description = "accessToken 위변조 또는 만료"),
            @ApiResponse(responseCode = "404", description = "뽑기 대상 역이 없음"),
    })
    @PostMapping
    public CommonResponse<RandomRecommendationResponse> drawRandom(
            @Parameter(hidden = true) @AuthenticationPrincipal(required = false) JwtPrincipal principal,
            @Valid @RequestBody RandomRecommendationRequest request) {
        Long memberId = (principal != null) ? principal.memberId() : null;
        return CommonResponse.success(recommendationCommandService.drawRandom(memberId, request));
    }

    @Operation(
            summary = "코스만 다시 뽑기",
            description = """
                    역은 그대로 두고 코스 미리보기만 다시 무작위로 뽑는다.
                    - 카테고리별 장소 1개씩으로 구성하며 장소가 없는 카테고리는 제외한다.
                    - 새로운 역 추천이 아니므로 recommendation_log에는 기록하지 않는다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "다시 뽑기 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 역"),
    })
    @PostMapping("/{stationId}/course")
    public CommonResponse<CoursePreviewResponse> redrawCourse(
            @Parameter(description = "코스를 다시 뽑을 역 ID", example = "10")
            @PathVariable Long stationId) {
        return CommonResponse.success(recommendationCommandService.redrawCourse(stationId));
    }
}
