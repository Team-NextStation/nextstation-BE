package com.cotato.nextstation.domain.block.controller;

import com.cotato.nextstation.domain.block.dto.response.BlockedMemberListResponse;
import com.cotato.nextstation.domain.block.service.command.MemberBlockCommandService;
import com.cotato.nextstation.domain.block.service.query.MemberBlockQueryService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MemberBlock")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/members/blocks")
public class MemberBlockController {

    private final MemberBlockCommandService memberBlockCommandService;
    private final MemberBlockQueryService memberBlockQueryService;

    @Operation(
            summary = "사용자 차단",
            description = "특정 회원을 차단한다. 차단 사유는 저장하지 않고 차단 시점만 기록한다."
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "차단 성공 (data 없음)"),
            @ApiResponse(responseCode = "400", description = "자기 자신을 차단 시도 (`MemberBlockErrorCode.SELF_BLOCK_NOT_ALLOWED`)"),
            @ApiResponse(responseCode = "401", description = "accessToken 누락, 위변조, 또는 만료 (`GlobalErrorCode.UNAUTHORIZED`, `GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원 (`MemberErrorCode.MEMBER_NOT_FOUND`)"),
            @ApiResponse(responseCode = "409", description = "이미 차단한 사용자 (`MemberBlockErrorCode.ALREADY_BLOCKED`)"),
    })
    @PostMapping("/{blockedMemberId}")
    public CommonResponse<Void> block(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable @Positive Long blockedMemberId) {
        memberBlockCommandService.block(principal.memberId(), blockedMemberId);
        return CommonResponse.success(null);
    }

    @Operation(
            summary = "사용자 차단 해제",
            description = "특정 회원에 대한 차단을 해제한다."
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "차단 해제 성공 (data 없음)"),
            @ApiResponse(responseCode = "401", description = "accessToken 누락, 위변조, 또는 만료 (`GlobalErrorCode.UNAUTHORIZED`, `GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
            @ApiResponse(responseCode = "404", description = "차단하지 않은 사용자 (`MemberBlockErrorCode.BLOCK_NOT_FOUND`)"),
    })
    @DeleteMapping("/{blockedMemberId}")
    public CommonResponse<Void> unblock(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable @Positive Long blockedMemberId) {
        memberBlockCommandService.unblock(principal.memberId(), blockedMemberId);
        return CommonResponse.success(null);
    }

    @Operation(
            summary = "차단한 사용자 목록 조회",
            description = "내가 차단한 사용자 목록을 최근 차단순으로 조회한다."
    )
    @SecurityRequirement(name = "accessTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "accessToken 누락, 위변조, 또는 만료 (`GlobalErrorCode.UNAUTHORIZED`, `GlobalErrorCode.INVALID_TOKEN`, `GlobalErrorCode.EXPIRED_TOKEN`)"),
    })
    @GetMapping
    public CommonResponse<BlockedMemberListResponse> getBlockedMembers(
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal principal) {
        return CommonResponse.success(memberBlockQueryService.getBlockedMembers(principal.memberId()));
    }
}
