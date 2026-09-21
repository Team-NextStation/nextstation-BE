package com.cotato.nextstation.domain.moderation.controller;

import com.cotato.nextstation.domain.moderation.dto.request.ContentReportRequest;
import com.cotato.nextstation.domain.moderation.dto.response.ContentReportResponse;
import com.cotato.nextstation.domain.moderation.service.command.ContentReportCommandService;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.security.AuthenticationPrincipal;
import com.cotato.nextstation.global.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "콘텐츠 신고")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
public class ContentReportController {

    private final ContentReportCommandService contentReportCommandService;

    @Operation(
            summary = "콘텐츠 신고",
            description = """
                여행일지 또는 장소 리뷰를 신고한다.
                - targetType으로 대상 종류를, targetId로 해당 대상의 id를 보낸다.
                - reason이 `ETC`인 경우 detail에 직접 입력한 내용을 담는다.
                - 같은 대상을 두 번 신고하면 실패한다.
                - 로그인 필수다. 토큰이 없으면 401이다.
                """
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "신고 접수 성공"),
            @ApiResponse(responseCode = "400", description = "필수값 누락 또는 허용되지 않는 값 (`GlobalErrorCode.VALIDATION_ERROR`)"),
            @ApiResponse(responseCode = "401", description = "accessToken이 없거나 위변조·만료 (`GlobalErrorCode.UNAUTHORIZED`, `GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
            @ApiResponse(responseCode = "409", description = "이미 신고한 콘텐츠 (`ReportErrorCode.REPORT_ALREADY_EXISTS`)"),
    })
    @PostMapping
    public CommonResponse<ContentReportResponse> report(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody ContentReportRequest request) {
        return CommonResponse.success(contentReportCommandService.report(principal.memberId(), request));
    }
}
