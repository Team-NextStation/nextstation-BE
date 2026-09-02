package com.cotato.nextstation.domain.auth.controller;

import com.cotato.nextstation.domain.auth.dto.request.AppleLoginRequest;
import com.cotato.nextstation.domain.auth.dto.request.AppleSignupRequest;
import com.cotato.nextstation.domain.auth.dto.response.AppleLoginResponse;
import com.cotato.nextstation.domain.auth.dto.response.SignupResponse;
import com.cotato.nextstation.domain.auth.service.command.AppleSignupCommandService;
import com.cotato.nextstation.domain.auth.service.query.AppleLoginQueryService;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResult;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResultType;
import com.cotato.nextstation.domain.auth.util.RefreshTokenCookieFactory;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.util.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/apple")
public class AppleAuthController {

    private final AppleLoginQueryService appleLoginQueryService;
    private final AppleSignupCommandService appleSignupCommandService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @Tag(name = "애플 로그인")
    @Operation(
            summary = "애플 로그인/신규가입 판별",
            description = """
                    iOS 네이티브 Sign In with Apple SDK가 발급한 identity token을 검증해 신규/기존 회원을 판별한다.
                    카카오와 달리 인가코드 교환이나 별도의 사용자정보조회 API 호출이 없다 - identity token 자체에 서명·클레임 검증을 수행한다.
                    - `resultType=LOGIN_SUCCESS`: 기존 ACTIVE 회원. accessToken은 응답 body로, refreshToken은 httpOnly 쿠키로 내려간다(로그인 API와 동일).
                    - `resultType=PENDING_PROFILE`: 프로필 설정이 끝나지 않은 회원. `signupToken`이 발급되며, 이후 흐름은 회원가입의 `/profile` 호출과 동일하다.
                    - `resultType=NEW_MEMBER`: 처음 보는 Apple 계정. `appleSignupToken`이 발급된다. 이 값을 들고 약관 동의 화면을 보여준 뒤 `/apple/signup`을 호출해야 한다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "판별 성공 (resultType으로 분기)"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 (`GlobalErrorCode.VALIDATION_ERROR`)"),
            @ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 identity token (`AuthErrorCode.INVALID_APPLE_IDENTITY_TOKEN`, `AuthErrorCode.APPLE_IDENTITY_TOKEN_EXPIRED`)"),
            @ApiResponse(responseCode = "403", description = "이용이 제한된 계정 (`AuthErrorCode.APPLE_MEMBER_NOT_ACTIVE`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원 (`AuthErrorCode.MEMBER_NOT_FOUND`)"),
            @ApiResponse(responseCode = "502", description = "Apple 서버(JWKS) 통신 오류 (`GlobalErrorCode.EXTERNAL_API_ERROR`)"),
    })
    @PostMapping("/login")
    public CommonResponse<AppleLoginResponse> appleLogin(@Valid @RequestBody AppleLoginRequest request,
                                                           HttpServletResponse httpResponse) {
        AppleLoginResult result = appleLoginQueryService.login(request.identityToken());

        if (result.resultType() == AppleLoginResultType.LOGIN_SUCCESS) {
            ResponseCookie refreshTokenCookie = refreshTokenCookieFactory.create(result.refreshToken());
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
        }

        AppleLoginResponse response = new AppleLoginResponse(
                result.resultType(),
                result.memberId(),
                result.accessToken(),
                result.signupToken(),
                result.appleSignupToken(),
                result.restored()
        );
        return CommonResponse.success(response);
    }

    @Tag(name = "애플 로그인")
    @Operation(
            summary = "애플 신규 회원가입 완료(약관 동의)",
            description = """
                    `/apple/login` 응답이 `resultType=NEW_MEMBER`였던 경우에만 호출한다.
                    - Member(PENDING) 생성과 약관 동의(`member_terms_agreement`) 저장, Apple 계정 연동(`member_social_account`)이 한 트랜잭션으로 처리된다.
                    - 응답으로 받는 `signupToken`은 이후 `/profile` 호출에 그대로 사용한다(자체가입 흐름과 동일).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공 또는 PENDING 회원 signupToken 재발급 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 필수 약관 미동의 (`GlobalErrorCode.VALIDATION_ERROR`, `TermsErrorCode.REQUIRED_TERMS_NOT_AGREED`)"),
            @ApiResponse(responseCode = "401", description = "appleSignupToken 누락, 위변조, 또는 만료 (`AuthErrorCode.INVALID_APPLE_SIGNUP_TOKEN`, `AuthErrorCode.APPLE_SIGNUP_TOKEN_EXPIRED`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 약관 id (`TermsErrorCode.TERMS_NOT_FOUND`)"),
            @ApiResponse(responseCode = "409", description = "이미 가입된 Apple 계정, 또는 Apple 인증 이메일이 기존 로컬 계정과 중복 (`AuthErrorCode.APPLE_ACCOUNT_ALREADY_REGISTERED`, `AuthErrorCode.DUPLICATE_EMAIL`)"),
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public CommonResponse<SignupResponse> appleSignup(@Valid @RequestBody AppleSignupRequest request,
                                                        HttpServletRequest httpRequest) {
        SignupResponse response = appleSignupCommandService.signup(
                request.appleSignupToken(),
                request.agreedTermsIds(),
                ClientIpResolver.resolve(httpRequest)
        );
        return CommonResponse.success(HttpStatus.CREATED, response);
    }
}
