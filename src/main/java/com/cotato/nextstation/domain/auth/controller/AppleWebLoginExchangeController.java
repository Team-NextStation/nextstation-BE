package com.cotato.nextstation.domain.auth.controller;

import com.cotato.nextstation.domain.auth.dto.request.AppleWebLoginExchangeRequest;
import com.cotato.nextstation.domain.auth.dto.response.AppleLoginResponse;
import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.repository.AppleWebLoginCodeRepository;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.exception.CustomException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웹 Apple 로그인 콜백({@link AppleWebAuthController#callback})이 리다이렉트로 전달한 1회용 code를
 * 실제 로그인 결과(accessToken 등)로 교환하는 API. accessToken을 리다이렉트 쿼리 파라미터로 직접 노출하지
 * 않기 위해 콜백과 분리했다 - code 자체는 1회성이라 브라우저 히스토리/로그에 남아도 재사용할 수 없다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/apple/web")
public class AppleWebLoginExchangeController {

    private final AppleWebLoginCodeRepository appleWebLoginCodeRepository;

    @Tag(name = "애플 로그인")
    @Operation(
            summary = "웹 Apple 로그인 콜백 code 교환",
            description = "/apple/web/callback 리다이렉트의 code 쿼리 파라미터로 받은 값을 실제 로그인 결과로 교환한다. " +
                    "code는 1회용이며 교환 즉시 폐기된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "교환 성공 (resultType으로 분기)"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 (`GlobalErrorCode.VALIDATION_ERROR`)"),
            @ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된(또는 이미 사용된) code (`AuthErrorCode.INVALID_APPLE_WEB_LOGIN_CODE`)"),
    })
    @PostMapping("/exchange")
    public CommonResponse<AppleLoginResponse> exchange(@Valid @RequestBody AppleWebLoginExchangeRequest request) {
        AppleLoginResponse response = appleWebLoginCodeRepository.consume(request.code())
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_APPLE_WEB_LOGIN_CODE));
        return CommonResponse.success(response);
    }
}
