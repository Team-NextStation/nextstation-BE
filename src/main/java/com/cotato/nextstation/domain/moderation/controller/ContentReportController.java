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
                여행일지, 장소 리뷰 또는 다른 이용자의 프로필을 신고한다.
                - targetType으로 대상 종류를, targetId로 해당 대상의 id를 보낸다.
                - `PROFILE`은 targetId에 신고할 회원의 memberId를 보낸다.
                - 삭제됐거나 존재하지 않는 대상은 신고할 수 없다. (탈퇴한 회원 포함)
                - 본인이 작성한 콘텐츠와 본인 프로필은 신고할 수 없다.
                - 같은 대상을 두 번 신고하면 실패한다.
                - 로그인 필수다. 토큰이 없으면 401이다.
                """
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "신고 접수 성공"),
            @ApiResponse(responseCode = "400", description = """
                    필수값 누락 또는 허용되지 않는 값 (`GlobalErrorCode.VALIDATION_ERROR`)
                    또는 본인이 작성한 콘텐츠·본인 프로필을 신고 (`ReportErrorCode.SELF_REPORT_NOT_ALLOWED`)"""),
            @ApiResponse(responseCode = "401", description = "accessToken이 없거나 위변조·만료 (`GlobalErrorCode.UNAUTHORIZED`, `GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 신고 대상 (`ReportErrorCode.REPORT_TARGET_NOT_FOUND`)"),
            @ApiResponse(responseCode = "409", description = "이미 신고한 대상 (`ReportErrorCode.REPORT_ALREADY_EXISTS`)"),
    })
    @PostMapping
    public CommonResponse<ContentReportResponse> report(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody ContentReportRequest request) {
        return CommonResponse.success(contentReportCommandService.report(principal.memberId(), request));
    }
}
