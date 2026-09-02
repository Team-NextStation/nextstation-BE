package com.cotato.nextstation.domain.auth.controller;

import com.cotato.nextstation.domain.auth.dto.request.KakaoLoginRequest;
import com.cotato.nextstation.domain.auth.dto.request.KakaoSignupRequest;
import com.cotato.nextstation.domain.auth.dto.response.KakaoLoginResponse;
import com.cotato.nextstation.domain.auth.dto.response.SignupResponse;
import com.cotato.nextstation.domain.auth.service.command.KakaoSignupCommandService;
import com.cotato.nextstation.domain.auth.service.query.KakaoLoginQueryService;
import com.cotato.nextstation.domain.auth.service.result.KakaoLoginResult;
import com.cotato.nextstation.domain.auth.service.result.KakaoLoginResultType;
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
@RequestMapping("/api/v1/auth/kakao")
public class KakaoAuthController {

    private final KakaoLoginQueryService kakaoLoginQueryService;
    private final KakaoSignupCommandService kakaoSignupCommandService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @Tag(name = "카카오 로그인")
    @Operation(
            summary = "카카오 로그인/신규가입 판별",
            description = """
                    카카오 인가코드를 받아 토큰교환→사용자정보조회 후 신규/기존 회원을 판별한다.
                    - `resultType=LOGIN_SUCCESS`: 기존 ACTIVE 회원. accessToken은 응답 body로, refreshToken은 httpOnly 쿠키로 내려간다(로그인 API와 동일).
                    - `resultType=PENDING_PROFILE`: 프로필 설정이 끝나지 않은 회원. `signupToken`이 발급되며, 이후 흐름은 회원가입의 `/profile` 호출과 동일하다.
                    - `resultType=NEW_MEMBER`: 처음 보는 카카오 계정. `kakaoSignupToken`이 발급된다. 이 값을 들고 약관 동의 화면을 보여준 뒤 `/kakao/signup`을 호출해야 한다.

                    `redirectUri`는 인가코드를 발급받을 때 사용한 값을 그대로 보낸다. 다른 값을 보내면 카카오가 인가코드를 거부한다(KOE303).
                    생략하면 서버에 설정된 대표 URI를 사용하므로, 프론트가 여러 도메인에서 서비스되는 경우에는 반드시 함께 보내야 한다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "판별 성공 (resultType으로 분기)"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패, 유효하지 않거나 만료된 인가코드, 또는 허용되지 않은 redirectUri (`GlobalErrorCode.VALIDATION_ERROR`, `AuthErrorCode.INVALID_KAKAO_CODE`, `AuthErrorCode.UNREGISTERED_REDIRECT_URI`)"),
            @ApiResponse(responseCode = "403", description = "이용이 제한된 계정 (`AuthErrorCode.KAKAO_MEMBER_NOT_ACTIVE`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원 (`AuthErrorCode.MEMBER_NOT_FOUND`)"),
            @ApiResponse(responseCode = "502", description = "카카오 서버 통신 오류 (`GlobalErrorCode.EXTERNAL_API_ERROR`)"),
    })
    @PostMapping("/login")
    public CommonResponse<KakaoLoginResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request,
                                                          HttpServletResponse httpResponse) {
        KakaoLoginResult result = kakaoLoginQueryService.login(request.code(), request.redirectUri());

        if (result.resultType() == KakaoLoginResultType.LOGIN_SUCCESS) {
            ResponseCookie refreshTokenCookie = refreshTokenCookieFactory.create(result.refreshToken());
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());
        }

        KakaoLoginResponse response = new KakaoLoginResponse(
                result.resultType(),
                result.memberId(),
                result.accessToken(),
                result.signupToken(),
                result.kakaoSignupToken(),
                result.kakaoNickname(),
                result.kakaoProfileImageUrl(),
                result.restored(),
                result.role()
        );
        return CommonResponse.success(response);
    }

    @Tag(name = "카카오 로그인")
    @Operation(
            summary = "카카오 신규 회원가입 완료(약관 동의)",
            description = """
                    `/kakao/login` 응답이 `resultType=NEW_MEMBER`였던 경우에만 호출한다.
                    - Member(PENDING) 생성과 약관 동의(`member_terms_agreement`) 저장, 카카오 계정 연동(`member_social_account`)이 한 트랜잭션으로 처리된다.
                    - 응답으로 받는 `signupToken`은 이후 `/profile` 호출에 그대로 사용한다(자체가입 흐름과 동일).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공 또는 PENDING 회원 signupToken 재발급 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 필수 약관 미동의 (`GlobalErrorCode.VALIDATION_ERROR`, `TermsErrorCode.REQUIRED_TERMS_NOT_AGREED`)"),
            @ApiResponse(responseCode = "401", description = "kakaoSignupToken 누락, 위변조, 또는 만료 (`AuthErrorCode.INVALID_KAKAO_SIGNUP_TOKEN`, `AuthErrorCode.KAKAO_SIGNUP_TOKEN_EXPIRED`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 약관 id (`TermsErrorCode.TERMS_NOT_FOUND`)"),
            @ApiResponse(responseCode = "409", description = "이미 가입된 카카오 계정, 또는 카카오 인증 이메일이 기존 로컬 계정과 중복 (`AuthErrorCode.KAKAO_ACCOUNT_ALREADY_REGISTERED`, `AuthErrorCode.DUPLICATE_EMAIL`)"),
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public CommonResponse<SignupResponse> kakaoSignup(@Valid @RequestBody KakaoSignupRequest request,
                                                        HttpServletRequest httpRequest) {
        SignupResponse response = kakaoSignupCommandService.signup(
                request.kakaoSignupToken(),
                request.agreedTermsIds(),
                ClientIpResolver.resolve(httpRequest)
        );
        return CommonResponse.success(HttpStatus.CREATED, response);
    }
}