package com.cotato.nextstation.domain.auth.controller;

import com.cotato.nextstation.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.cotato.nextstation.domain.auth.dto.request.LoginRequest;
import com.cotato.nextstation.domain.auth.dto.request.PasswordResetRequest;
import com.cotato.nextstation.domain.auth.dto.request.PasswordResetSendRequest;
import com.cotato.nextstation.domain.auth.dto.request.ProfileSetupRequest;
import com.cotato.nextstation.domain.auth.dto.request.SignupRequest;
import com.cotato.nextstation.domain.auth.dto.request.SignupVerificationSendRequest;
import com.cotato.nextstation.domain.auth.dto.response.LoginResponse;
import com.cotato.nextstation.domain.auth.dto.response.ProfileSetupResponse;
import com.cotato.nextstation.domain.auth.dto.response.ReissueResponse;
import com.cotato.nextstation.domain.auth.dto.response.SignupResponse;
import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.service.command.EmailVerificationCommandService;
import com.cotato.nextstation.domain.auth.service.command.PasswordResetCommandService;
import com.cotato.nextstation.domain.auth.service.command.ProfileSetupCommandService;
import com.cotato.nextstation.domain.auth.service.command.SignupCommandService;
import com.cotato.nextstation.domain.auth.service.AuthTokenService;
import com.cotato.nextstation.domain.auth.service.result.LoginResult;
import com.cotato.nextstation.domain.auth.service.result.ProfileSetupResult;
import com.cotato.nextstation.domain.auth.service.result.ReissueResult;
import com.cotato.nextstation.domain.auth.util.RefreshTokenCookieFactory;
import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.util.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final EmailVerificationCommandService emailVerificationCommandService;
    private final SignupCommandService signupCommandService;
    private final ProfileSetupCommandService profileSetupCommandService;
    private final AuthTokenService authTokenService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final PasswordResetCommandService passwordResetCommandService;

    @Tag(name = "회원가입")
    @Operation(
            summary = "[1단계] 회원가입 이메일 인증번호 발송",
            description = """
                    입력한 이메일로 6자리 인증번호를 발송한다.
                    - 인증번호 유효시간: 3분
                    - 발송 한도: 시간당 5회 / 하루 10회, 초과 시 일정 시간 잠금
                    - 이미 PENDING 상태의 코드가 있으면 새 코드 발송 시 기존 코드는 즉시 무효화된다.
                    - 필수 약관에 모두 동의해야 발송된다(`agreedTermsIds`에 현재 필수 약관 id가 전부 포함돼야 함).
                    - 발송 후에는 인증번호 확인(2단계, `/email/verification/confirm`) API로 검증한 뒤, 회원가입 비밀번호 설정(3단계, `/signup`) API로 이어진다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발송 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 필수 약관 미동의 (`GlobalErrorCode.VALIDATION_ERROR`, `TermsErrorCode.REQUIRED_TERMS_NOT_AGREED`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 약관 id (`TermsErrorCode.TERMS_NOT_FOUND`)"),
            @ApiResponse(responseCode = "409", description = "이미 가입된 이메일 (`AuthErrorCode.DUPLICATE_EMAIL`)"),
            @ApiResponse(responseCode = "429", description = "발송 횟수 한도 초과 또는 잠금 상태 (`AuthErrorCode.EMAIL_VERIFICATION_RATE_LIMIT_EXCEEDED`)"),
            @ApiResponse(responseCode = "502", description = "메일 발송 실패 (`GlobalErrorCode.EXTERNAL_API_ERROR`)"),
    })
    @PostMapping("/email/verification")
    public CommonResponse<Void> sendEmailVerificationCode(@Valid @RequestBody SignupVerificationSendRequest request) {
        emailVerificationCommandService.sendSignupVerificationCode(request.email(), request.agreedTermsIds());
        return CommonResponse.success(null);
    }

    @Tag(name = "회원가입")
    @Operation(
            summary = "[2단계] 회원가입 이메일 인증번호 확인",
            description = """
                    발송된 6자리 인증번호가 맞는지 확인한다.
                    - 인증번호 불일치 시도는 최대 5회까지 허용되며, 초과 시 해당 인증번호는 실패 처리된다.
                    - 만료된 인증번호로 확인을 시도하면 즉시 실패 처리된다.
                    - 확인 성공 후에는 회원가입 비밀번호 설정(3단계, `/signup`) API를 호출해 회원(Member)을 생성한다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패, 만료된 인증번호, 또는 인증번호 불일치 (`GlobalErrorCode.VALIDATION_ERROR`, `AuthErrorCode.EMAIL_VERIFICATION_EXPIRED`, `AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH`)"),
            @ApiResponse(responseCode = "404", description = "유효한 인증번호 발송 내역 없음 (`AuthErrorCode.EMAIL_VERIFICATION_NOT_FOUND`)"),
            @ApiResponse(responseCode = "429", description = "인증번호 확인 시도 횟수 초과 (`AuthErrorCode.EMAIL_VERIFICATION_ATTEMPT_EXCEEDED`)"),
    })
    @PostMapping("/email/verification/confirm")
    public CommonResponse<Void> confirmEmailVerificationCode(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        emailVerificationCommandService.verifySignupCode(request.email(), request.code());
        return CommonResponse.success(null);
    }

    @Tag(name = "회원가입")
    @Operation(
            summary = "[3단계] 회원가입 비밀번호 설정",
            description = """
                    이메일 인증이 완료된 이메일로 회원(Member)을 생성하고 비밀번호를 설정한다.
                    - Member 생성과 약관 동의(`member_terms_agreement`) 저장이 한 트랜잭션으로 처리된다.
                    - 응답으로 받는 `signupToken`은 **다음 단계인 프로필 설정(`/profile`) API를 호출할 때만 쓰는 전용 토큰**이다.
                      로그인 상태를 유지하는 access token이 아니므로 다른 API 호출에는 쓸 수 없고, 발급 후 30분이 지나면 만료된다.
                    - 프로필 설정 화면으로 넘어갈 때 이 값을 프론트에서 잠깐 들고 있다가, 프로필 설정 API 요청의 `Authorization: Bearer {signupToken}` 헤더에 그대로 실어서 보내면 된다.
                    - access token(로그인 유지용)과 refresh token은 이 API가 아니라 로그인 API에서 발급된다.
                    - signupToken이 만료된 뒤 돌아온 **PENDING 회원**이 같은 이메일/비밀번호로 다시 요청하면, 새로 가입시키지 않고 **signupToken만 재발급**한다(비밀번호가 재인증 역할). 이미 프로필 설정까지 끝난 회원은 그대로 `DUPLICATE_EMAIL`로 거부된다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공 또는 PENDING 회원 signupToken 재발급 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패, 비밀번호 확인 불일치, 이메일 인증 미완료, 또는 필수 약관 미동의 (`GlobalErrorCode.VALIDATION_ERROR`, `AuthErrorCode.PASSWORD_CONFIRMATION_MISMATCH`, `AuthErrorCode.EMAIL_NOT_VERIFIED`, `TermsErrorCode.REQUIRED_TERMS_NOT_AGREED`)"),
            @ApiResponse(responseCode = "401", description = "PENDING 회원의 비밀번호 불일치 (`AuthErrorCode.PASSWORD_MISMATCH`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 약관 id (`TermsErrorCode.TERMS_NOT_FOUND`)"),
            @ApiResponse(responseCode = "409", description = "이미 프로필 설정까지 완료된 이메일 (`AuthErrorCode.DUPLICATE_EMAIL`)"),
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public CommonResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        SignupResponse response = signupCommandService.signup(
                request.email(),
                request.password(),
                request.passwordConfirm(),
                request.agreedTermsIds(),
                ClientIpResolver.resolve(httpRequest)
        );
        return CommonResponse.success(HttpStatus.CREATED, response);
    }

    @Tag(name = "프로필 설정")
    @Operation(
            summary = "프로필 설정",
            description = """
                    닉네임/프로필 사진/성별/생년월일을 설정하고 회원가입을 완료한다(status: PENDING -> ACTIVE).
                    - 로컬 회원가입(`/signup`)과 카카오 회원가입(`/kakao/login`의 `PENDING_PROFILE` 또는 `/kakao/signup`) 양쪽 흐름의 마지막 단계로 공통으로 쓰인다.
                    - **설정 완료와 동시에 로그인 처리된다.** accessToken은 응답 body로, refreshToken은 httpOnly 쿠키(`refreshToken`)로 내려가므로 별도의 로그인 API 호출이 필요 없다(로그인 API와 동일).
                      쿠키가 저장되려면 이 API 요청에도 `credentials: 'include'`가 필요하다.
                    - signupToken 인증 필요. 우측 상단 자물쇠(Authorize) 버튼을 눌러 위 API들의 응답으로 받은 signupToken 값을 그대로(Bearer 접두사 없이) 넣으면 된다.
                    - 프로필 사진은 선택 입력이며, presigned URL로 S3에 업로드 완료 후 받은 imageUrl을 그대로 넣으면 된다.
                    - 성별의 "선택 안함"도 `UNSPECIFIED`로 명시적으로 보내야 한다.
                    - 이미 프로필 설정이 완료된 회원(status != PENDING)이 같은 토큰으로 다시 요청하면 거부된다(재사용 방지).
                    """
    )
    @SecurityRequirement(name = "signupTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "프로필 설정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패, 닉네임 길이/문자/금칙어/예약어 위반 (`GlobalErrorCode.VALIDATION_ERROR`, `NicknameErrorCode.NICKNAME_TOO_SHORT`, `NicknameErrorCode.NICKNAME_TOO_LONG`, `NicknameErrorCode.NICKNAME_INVALID_CHARACTER`, `NicknameErrorCode.NICKNAME_CONTAINS_BANNED_WORD`, `NicknameErrorCode.NICKNAME_CONTAINS_RESERVED_WORD`)"),
            @ApiResponse(responseCode = "401", description = "signupToken 누락, 위변조, 또는 만료 (`AuthErrorCode.INVALID_SIGNUP_TOKEN`, `AuthErrorCode.SIGNUP_TOKEN_EXPIRED`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원 (`AuthErrorCode.MEMBER_NOT_FOUND`)"),
            @ApiResponse(responseCode = "409", description = "이미 프로필 설정 완료됨 또는 닉네임 중복 (`AuthErrorCode.PROFILE_ALREADY_COMPLETED`, `NicknameErrorCode.DUPLICATE_NICKNAME`)"),
    })
    @PostMapping("/profile")
    public CommonResponse<ProfileSetupResponse> setupProfile(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false, defaultValue = "") String authorizationHeader,
            @Valid @RequestBody ProfileSetupRequest request,
            HttpServletResponse httpResponse) {
        ProfileSetupResult result = profileSetupCommandService.setupProfile(
                authorizationHeader,
                request.nickname(),
                request.profileImageUrl(),
                request.gender(),
                request.birthDate()
        );

        ResponseCookie refreshTokenCookie = refreshTokenCookieFactory.create(result.refreshToken());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        return CommonResponse.success(
                new ProfileSetupResponse(result.memberId(), result.nickname(), result.status(), result.accessToken()));
    }

    @Tag(name = "로그인")
    @Operation(
            summary = "로그인",
            description = """
                    이메일/비밀번호로 로그인하고 access token을 발급한다.
                    - access token은 응답 body로 내려가며, 다른 API 호출 시 `Authorization: Bearer {accessToken}` 헤더에 실어서 보낸다. 발급 후 1시간이 지나면 만료된다.
                    - refresh token은 httpOnly 쿠키(`refreshToken`)로 내려가며, 프론트에서 직접 다루지 않는다. 발급 후 14일이 지나면 만료된다.
                    - 존재하지 않는 이메일, 비밀번호 불일치, 프로필 설정이 끝나지 않은 회원(PENDING) 모두 동일하게 `INVALID_CREDENTIALS`로 응답한다(계정 존재 여부 노출 방지).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 (`GlobalErrorCode.VALIDATION_ERROR`)"),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치 (`AuthErrorCode.INVALID_CREDENTIALS`)"),
    })
    @PostMapping("/login")
    public CommonResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse httpResponse) {
        LoginResult result = authTokenService.login(request.email(), request.password());

        ResponseCookie refreshTokenCookie = refreshTokenCookieFactory.create(result.refreshToken());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        return CommonResponse.success(new LoginResponse(result.memberId(), result.accessToken(), result.restored(), result.role()));
    }

    @Tag(name = "로그인")
    @Operation(
            summary = "액세스 토큰 재발급",
            description = """
                    로그인 시 발급된 httpOnly 쿠키(`refreshToken`)를 검증해 새 accessToken을 발급한다.
                    - accessToken이 만료(401)되면 이 API를 호출해 새 accessToken을 받고, 원래 요청을 재시도하면 된다.
                    - **rotation**: 호출할 때마다 refreshToken도 함께 새로 발급되어 쿠키가 갱신된다(`Set-Cookie`). httpOnly라 프론트에서 값을 직접 다루지 않아도 되며, `credentials: 'include'`만 유지하면 브라우저가 새 쿠키를 자동 반영한다.
                    - **세션 연장(sliding)**: 이 API를 호출할 때마다 로그인 유지 기간이 14일로 다시 채워진다. 다만 최초 로그인 후 90일이 지나면 사용 여부와 무관하게 재로그인이 필요하다.
                    - **재사용 탐지**: 이미 rotate되어 무효화된 옛 refreshToken이 다시 사용되면 탈취 의심으로 간주해 해당 세션을 즉시 종료하고 `REFRESH_TOKEN_REUSE_DETECTED`(401)를 반환한다. 이 경우 재로그인이 필요하다.
                    - 단, rotate 직후 10초 이내에 같은 refreshToken으로 들어온 요청은 멀티탭/병렬 호출로 보고 정상 처리한다(탐지 대상 아님). 그래도 프론트에서 401 발생 시 재발급 요청을 하나로 합치는(single-flight) 처리를 권장한다.
                    - Swagger UI에서 테스트하려면 우측 상단 자물쇠(Authorize) 버튼을 눌러 로그인 API 응답 쿠키의 refreshToken 값을(접두사 없이) 넣으면 된다.
                    """
    )
    @SecurityRequirement(name = "refreshTokenAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "401", description = "refreshToken 누락, 위변조, purpose 불일치, 만료, 또는 재사용 탐지 (`AuthErrorCode.INVALID_REFRESH_TOKEN`, `AuthErrorCode.REFRESH_TOKEN_EXPIRED`, `AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED`)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원 (`AuthErrorCode.MEMBER_NOT_FOUND`)"),
    })
    @PostMapping("/reissue")
    public CommonResponse<ReissueResponse> reissue(
            @Parameter(hidden = true)
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse httpResponse) {
        // 로그아웃 응답이 쿠키 값을 빈 문자열로 덮으므로, 만료를 무시하고 되돌려보내는 클라이언트가 있으면 빈 값이 들어온다.
        // JWT 파서는 빈 문자열에 JwtException이 아닌 IllegalArgumentException을 던져 500이 되므로 여기서 걸러낸다.
        if (!StringUtils.hasText(refreshToken)) {
            throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        ReissueResult result = authTokenService.reissue(refreshToken);

        ResponseCookie refreshTokenCookie = refreshTokenCookieFactory.create(result.refreshToken());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        return CommonResponse.success(new ReissueResponse(result.accessToken(), result.role()));
    }

    @Tag(name = "로그인")
    @Operation(
            summary = "로그아웃",
            description = """
                    현재 기기의 로그인 세션을 종료한다.
                    - httpOnly 쿠키(`refreshToken`)를 읽어 서버의 세션(refresh 재발급 이력)을 무효화하고, 쿠키를 즉시 만료시켜 응답한다.
                    - 쿠키가 없거나 이미 만료/무효한 상태에서 호출해도 에러 없이 성공(200)한다(멱등) — 이미 로그아웃된 상태를 다시 요청한 것으로 간주한다.
                    - accessToken은 이 API로 즉시 무효화되지 않는다. 발급 후 최대 1시간까지는 자연 만료 전까지 유효하다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공(쿠키 없음/무효한 토큰이어도 항상 200)"),
    })
    @PostMapping("/logout")
    public CommonResponse<Void> logout(
            @Parameter(hidden = true)
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse httpResponse) {
        // 값이 빈 쿠키는 보내지 않은 것과 같게 취급한다 (빈 문자열은 JWT 파서에서 IllegalArgumentException -> 500).
        if (StringUtils.hasText(refreshToken)) {
            authTokenService.logout(refreshToken);
        }

        ResponseCookie expiredCookie = refreshTokenCookieFactory.createExpired();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());

        return CommonResponse.success(null);
    }

    @Tag(name = "비밀번호 재설정")
    @Operation(
            summary = "[1단계] 비밀번호 재설정 이메일 인증번호 발송",
            description = """
                    가입된 로컬 계정 이메일로 6자리 인증번호를 발송한다.
                    - 인증번호 유효시간: 3분
                    - 발송 한도: 시간당 5회 / 하루 10회, 초과 시 일정 시간 잠금 (회원가입 인증번호와는 별도로 집계된다)
                    - 이미 PENDING 상태의 코드가 있으면 새 코드 발송 시 기존 코드는 즉시 무효화된다.
                    - 소셜 로그인 전용 계정(비밀번호가 없는 계정)은 재설정 대상에서 제외된다.
                    - 발송 후에는 인증번호 확인(2단계, `/password-reset/email/verification/confirm`) API로 검증한 뒤, 비밀번호 재설정(3단계, `/password-reset`) API로 이어진다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발송 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패 또는 소셜 로그인 전용 계정 (`GlobalErrorCode.VALIDATION_ERROR`, `AuthErrorCode.SOCIAL_ONLY_ACCOUNT`)"),
            @ApiResponse(responseCode = "403", description = "탈퇴/정지 등 ACTIVE 상태가 아닌 회원 (`AuthErrorCode.MEMBER_NOT_ACTIVE`)"),
            @ApiResponse(responseCode = "404", description = "가입되지 않은 이메일 (`AuthErrorCode.MEMBER_NOT_FOUND`)"),
            @ApiResponse(responseCode = "429", description = "발송 횟수 한도 초과 또는 잠금 상태 (`AuthErrorCode.EMAIL_VERIFICATION_RATE_LIMIT_EXCEEDED`)"),
            @ApiResponse(responseCode = "502", description = "메일 발송 실패 (`GlobalErrorCode.EXTERNAL_API_ERROR`)"),
    })
    @PostMapping("/password-reset/email/verification")
    public CommonResponse<Void> sendPasswordResetVerificationCode(@Valid @RequestBody PasswordResetSendRequest request) {
        emailVerificationCommandService.sendPasswordResetVerificationCode(request.email());
        return CommonResponse.success(null);
    }

    @Tag(name = "비밀번호 재설정")
    @Operation(
            summary = "[2단계] 비밀번호 재설정 이메일 인증번호 확인",
            description = """
                    발송된 6자리 인증번호가 맞는지 확인한다.
                    - 인증번호 불일치 시도는 최대 5회까지 허용되며, 초과 시 해당 인증번호는 실패 처리된다.
                    - 만료된 인증번호로 확인을 시도하면 즉시 실패 처리된다.
                    - 확인 성공 후에는 비밀번호 재설정(3단계, `/password-reset`) API를 호출해 새 비밀번호로 변경한다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패, 만료된 인증번호, 또는 인증번호 불일치 (`GlobalErrorCode.VALIDATION_ERROR`, `AuthErrorCode.EMAIL_VERIFICATION_EXPIRED`, `AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH`)"),
            @ApiResponse(responseCode = "404", description = "유효한 인증번호 발송 내역 없음 (`AuthErrorCode.EMAIL_VERIFICATION_NOT_FOUND`)"),
            @ApiResponse(responseCode = "429", description = "인증번호 확인 시도 횟수 초과 (`AuthErrorCode.EMAIL_VERIFICATION_ATTEMPT_EXCEEDED`)"),
    })
    @PostMapping("/password-reset/email/verification/confirm")
    public CommonResponse<Void> confirmPasswordResetVerificationCode(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        emailVerificationCommandService.verifyPasswordResetCode(request.email(), request.code());
        return CommonResponse.success(null);
    }

    @Tag(name = "비밀번호 재설정")
    @Operation(
            summary = "[3단계] 비밀번호 재설정",
            description = """
                    이메일 인증이 완료된 로컬 계정의 비밀번호를 새 비밀번호로 변경한다.
                    - 인증번호 확인(confirm) 이후 비밀번호 입력까지 시간이 걸릴 수 있으므로, 재설정 시점에 인증번호 일치/만료 여부를 다시 검증한다.
                      이때의 불일치 시도도 confirm 단계와 동일한 인증번호 확인 시도 횟수(최대 5회)에 합산된다.
                    - 비밀번호 변경에 성공하면 해당 인증번호는 즉시 만료 처리되어 재사용할 수 없다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재설정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패, 새 비밀번호 확인 불일치, 만료된 인증번호, 또는 인증번호 불일치 (`GlobalErrorCode.VALIDATION_ERROR`, `AuthErrorCode.PASSWORD_CONFIRMATION_MISMATCH`, `AuthErrorCode.EMAIL_VERIFICATION_EXPIRED`, `AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH`)"),
            @ApiResponse(responseCode = "403", description = "탈퇴/정지 등 ACTIVE 상태가 아닌 회원 (`AuthErrorCode.MEMBER_NOT_ACTIVE`)"),
            @ApiResponse(responseCode = "404", description = "인증 완료 내역 없음 또는 존재하지 않는 회원 (`AuthErrorCode.EMAIL_VERIFICATION_NOT_FOUND`, `AuthErrorCode.MEMBER_NOT_FOUND`)"),
            @ApiResponse(responseCode = "429", description = "인증번호 확인 시도 횟수 초과 (`AuthErrorCode.EMAIL_VERIFICATION_ATTEMPT_EXCEEDED`)"),
    })
    @PostMapping("/password-reset")
    public CommonResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetCommandService.resetPassword(
                request.email(),
                request.code(),
                request.newPassword(),
                request.newPasswordConfirm()
        );
        return CommonResponse.success(null);
    }
}