package com.cotato.nextstation.domain.auth.controller;

import com.cotato.nextstation.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.cotato.nextstation.domain.auth.dto.request.LoginRequest;
import com.cotato.nextstation.domain.auth.dto.request.PasswordResetRequest;
import com.cotato.nextstation.domain.auth.dto.request.PasswordResetSendRequest;
import com.cotato.nextstation.domain.auth.dto.request.ProfileSetupRequest;
import com.cotato.nextstation.domain.auth.dto.request.SignupRequest;
import com.cotato.nextstation.domain.auth.dto.request.SignupVerificationSendRequest;
import com.cotato.nextstation.domain.auth.service.result.ProfileSetupResult;
import com.cotato.nextstation.domain.auth.dto.response.SignupResponse;
import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.exception.TermsErrorCode;
import com.cotato.nextstation.domain.auth.service.command.EmailVerificationCommandService;
import com.cotato.nextstation.domain.auth.service.command.PasswordResetCommandService;
import com.cotato.nextstation.domain.auth.service.command.ProfileSetupCommandService;
import com.cotato.nextstation.domain.auth.service.command.SignupCommandService;
import com.cotato.nextstation.domain.auth.service.AuthTokenService;
import com.cotato.nextstation.domain.auth.service.result.LoginResult;
import com.cotato.nextstation.domain.auth.service.result.ReissueResult;
import com.cotato.nextstation.domain.auth.util.RefreshTokenCookieFactory;
import com.cotato.nextstation.domain.member.entity.Gender;
import com.cotato.nextstation.domain.member.entity.MemberRole;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.exception.NicknameErrorCode;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    EmailVerificationCommandService emailVerificationCommandService;

    @MockitoBean
    SignupCommandService signupCommandService;

    @MockitoBean
    ProfileSetupCommandService profileSetupCommandService;

    @MockitoBean
    AuthTokenService authTokenService;

    @MockitoBean
    RefreshTokenCookieFactory refreshTokenCookieFactory;

    @MockitoBean
    PasswordResetCommandService passwordResetCommandService;

    // WebConfig가 등록하는 JwtPrincipalArgumentResolver가 필요로 해서 @WebMvcTest 슬라이스에도 목이 필요하다
    @MockitoBean
    JwtProvider jwtProvider;

    @Test
    @DisplayName("동의한 약관 목록이 비어있으면 400을 반환한다")
    void sendEmailVerificationCode_agreedTermsIdsEmpty() throws Exception {
        SignupVerificationSendRequest request = new SignupVerificationSendRequest("user@example.com", List.of());

        mockMvc.perform(post("/api/v1/auth/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.reasons.agreedTermsIds").exists());
    }

    @Test
    @DisplayName("필수 약관을 동의하지 않으면 400을 반환한다")
    void sendEmailVerificationCode_requiredTermsNotAgreed() throws Exception {
        SignupVerificationSendRequest request = new SignupVerificationSendRequest("user@example.com", List.of(2L));
        willThrow(new CustomException(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED))
                .given(emailVerificationCommandService).sendSignupVerificationCode("user@example.com", List.of(2L));

        mockMvc.perform(post("/api/v1/auth/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED.getCode()));
    }

    @Test
    @DisplayName("필수 약관에 모두 동의하면 200을 반환한다")
    void sendEmailVerificationCode_success() throws Exception {
        SignupVerificationSendRequest request = new SignupVerificationSendRequest("user@example.com", List.of(1L, 2L));
        willDoNothing().given(emailVerificationCommandService).sendSignupVerificationCode("user@example.com", List.of(1L, 2L));

        mockMvc.perform(post("/api/v1/auth/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("비밀번호와 비밀번호 확인이 다르면 400을 반환한다")
    void signup_passwordConfirmationMismatch() throws Exception {
        SignupRequest request = new SignupRequest("user@example.com", "abc12345!", "different1!", List.of(1L));
        willThrow(new CustomException(AuthErrorCode.PASSWORD_CONFIRMATION_MISMATCH))
                .given(signupCommandService).signup(anyString(), anyString(), anyString(), any(), anyString());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.PASSWORD_CONFIRMATION_MISMATCH.getCode()));
    }

    @Test
    @DisplayName("이메일 인증이 완료되지 않았으면 400을 반환한다")
    void signup_emailNotVerified() throws Exception {
        SignupRequest request = new SignupRequest("user@example.com", "abc12345!", "abc12345!", List.of(1L));
        willThrow(new CustomException(AuthErrorCode.EMAIL_NOT_VERIFIED))
                .given(signupCommandService).signup(anyString(), anyString(), anyString(), any(), anyString());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.EMAIL_NOT_VERIFIED.getCode()));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 409를 반환한다")
    void signup_duplicateEmail() throws Exception {
        SignupRequest request = new SignupRequest("user@example.com", "abc12345!", "abc12345!", List.of(1L));
        willThrow(new CustomException(AuthErrorCode.DUPLICATE_EMAIL))
                .given(signupCommandService).signup(anyString(), anyString(), anyString(), any(), anyString());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.DUPLICATE_EMAIL.getCode()));
    }

    @Test
    @DisplayName("PENDING 회원의 비밀번호가 다르면 401을 반환한다")
    void signup_passwordMismatchForPendingMember() throws Exception {
        SignupRequest request = new SignupRequest("user@example.com", "abc12345!", "abc12345!", List.of(1L));
        willThrow(new CustomException(AuthErrorCode.PASSWORD_MISMATCH))
                .given(signupCommandService).signup(anyString(), anyString(), anyString(), any(), anyString());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.PASSWORD_MISMATCH.getCode()));
    }

    @Test
    @DisplayName("비밀번호 형식이 올바르지 않으면 400을 반환한다")
    void signup_invalidPasswordFormat() throws Exception {
        SignupRequest request = new SignupRequest("user@example.com", "short1!", "short1!", List.of(1L));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.reasons.password").exists());
    }

    @Test
    @DisplayName("정상 요청이면 201과 signupToken을 반환한다")
    void signup_success() throws Exception {
        SignupRequest request = new SignupRequest("user@example.com", "abc12345!", "abc12345!", List.of(1L));
        given(signupCommandService.signup(anyString(), anyString(), anyString(), any(), anyString()))
                .willReturn(new SignupResponse(1L, "signup-token"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.signupToken").value("signup-token"));
    }

    private static final String SIGNUP_TOKEN_HEADER = "Bearer signup-token";

    private ProfileSetupRequest profileSetupRequest(String nickname) {
        return new ProfileSetupRequest(nickname, null, Gender.MALE, LocalDate.of(2001, 1, 1));
    }

    @Test
    @DisplayName("정상 요청이면 200과 프로필 설정 결과·accessToken을 반환하고 refreshToken을 쿠키로 내려준다")
    void setupProfile_success() throws Exception {
        ProfileSetupRequest request = profileSetupRequest("환승러");
        given(profileSetupCommandService.setupProfile(SIGNUP_TOKEN_HEADER, "환승러", null, Gender.MALE, LocalDate.of(2001, 1, 1)))
                .willReturn(new ProfileSetupResult(1L, "환승러", MemberStatus.ACTIVE, "access-token", "refresh-token"));
        given(refreshTokenCookieFactory.create("refresh-token"))
                .willReturn(ResponseCookie.from("refreshToken", "refresh-token").httpOnly(true).build());

        mockMvc.perform(post("/api/v1/auth/profile")
                        .header("Authorization", SIGNUP_TOKEN_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.nickname").value("환승러"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(cookie().value("refreshToken", "refresh-token"))
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    @Test
    @DisplayName("닉네임이 비어있으면 400을 반환한다")
    void setupProfile_nicknameBlank() throws Exception {
        ProfileSetupRequest request = profileSetupRequest("");

        mockMvc.perform(post("/api/v1/auth/profile")
                        .header("Authorization", SIGNUP_TOKEN_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.reasons.nickname").exists());
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401을 반환한다")
    void setupProfile_missingAuthorizationHeader() throws Exception {
        ProfileSetupRequest request = profileSetupRequest("환승러");
        willThrow(new CustomException(AuthErrorCode.INVALID_SIGNUP_TOKEN))
                .given(profileSetupCommandService).setupProfile(anyString(), anyString(), any(), any(), any());

        mockMvc.perform(post("/api/v1/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_SIGNUP_TOKEN.getCode()));
    }

    @Test
    @DisplayName("토큰이 만료됐으면 401을 반환한다")
    void setupProfile_expiredToken() throws Exception {
        ProfileSetupRequest request = profileSetupRequest("환승러");
        willThrow(new CustomException(AuthErrorCode.SIGNUP_TOKEN_EXPIRED))
                .given(profileSetupCommandService).setupProfile(anyString(), anyString(), any(), any(), any());

        mockMvc.perform(post("/api/v1/auth/profile")
                        .header("Authorization", SIGNUP_TOKEN_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.SIGNUP_TOKEN_EXPIRED.getCode()));
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 404를 반환한다")
    void setupProfile_memberNotFound() throws Exception {
        ProfileSetupRequest request = profileSetupRequest("환승러");
        willThrow(new CustomException(AuthErrorCode.MEMBER_NOT_FOUND))
                .given(profileSetupCommandService).setupProfile(anyString(), anyString(), any(), any(), any());

        mockMvc.perform(post("/api/v1/auth/profile")
                        .header("Authorization", SIGNUP_TOKEN_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.MEMBER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("이미 프로필 설정이 완료된 회원이면 409를 반환한다")
    void setupProfile_alreadyCompleted() throws Exception {
        ProfileSetupRequest request = profileSetupRequest("환승러");
        willThrow(new CustomException(AuthErrorCode.PROFILE_ALREADY_COMPLETED))
                .given(profileSetupCommandService).setupProfile(anyString(), anyString(), any(), any(), any());

        mockMvc.perform(post("/api/v1/auth/profile")
                        .header("Authorization", SIGNUP_TOKEN_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.PROFILE_ALREADY_COMPLETED.getCode()));
    }

    @Test
    @DisplayName("닉네임이 중복되면 409를 반환한다")
    void setupProfile_duplicateNickname() throws Exception {
        ProfileSetupRequest request = profileSetupRequest("환승러");
        willThrow(new CustomException(NicknameErrorCode.DUPLICATE_NICKNAME))
                .given(profileSetupCommandService).setupProfile(anyString(), anyString(), any(), any(), any());

        mockMvc.perform(post("/api/v1/auth/profile")
                        .header("Authorization", SIGNUP_TOKEN_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(NicknameErrorCode.DUPLICATE_NICKNAME.getCode()));
    }

    @Test
    @DisplayName("닉네임이 2자 미만이면 400을 반환한다")
    void setupProfile_nicknameTooShort() throws Exception {
        ProfileSetupRequest request = profileSetupRequest("환");
        willThrow(new CustomException(NicknameErrorCode.NICKNAME_TOO_SHORT))
                .given(profileSetupCommandService).setupProfile(anyString(), anyString(), any(), any(), any());

        mockMvc.perform(post("/api/v1/auth/profile")
                        .header("Authorization", SIGNUP_TOKEN_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(NicknameErrorCode.NICKNAME_TOO_SHORT.getCode()));
    }

    @Test
    @DisplayName("닉네임이 10자를 초과하면 400을 반환한다")
    void setupProfile_nicknameTooLong() throws Exception {
        ProfileSetupRequest request = profileSetupRequest("환승러환승러환승러환승러");
        willThrow(new CustomException(NicknameErrorCode.NICKNAME_TOO_LONG))
                .given(profileSetupCommandService).setupProfile(anyString(), anyString(), any(), any(), any());

        mockMvc.perform(post("/api/v1/auth/profile")
                        .header("Authorization", SIGNUP_TOKEN_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(NicknameErrorCode.NICKNAME_TOO_LONG.getCode()));
    }

    @Test
    @DisplayName("닉네임에 허용되지 않은 문자가 포함되면 400을 반환한다")
    void setupProfile_nicknameInvalidCharacter() throws Exception {
        ProfileSetupRequest request = profileSetupRequest("환승러!!");
        willThrow(new CustomException(NicknameErrorCode.NICKNAME_INVALID_CHARACTER))
                .given(profileSetupCommandService).setupProfile(anyString(), anyString(), any(), any(), any());

        mockMvc.perform(post("/api/v1/auth/profile")
                        .header("Authorization", SIGNUP_TOKEN_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(NicknameErrorCode.NICKNAME_INVALID_CHARACTER.getCode()));
    }

    @Test
    @DisplayName("정상 로그인이면 200과 accessToken을 반환하고 refreshToken을 쿠키로 내려준다")
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "abc12345!");
        given(authTokenService.login("user@example.com", "abc12345!"))
                .willReturn(new LoginResult(1L, "access-token", "refresh-token", false, MemberRole.USER));
        given(refreshTokenCookieFactory.create("refresh-token"))
                .willReturn(ResponseCookie.from("refreshToken", "refresh-token").httpOnly(true).build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(cookie().value("refreshToken", "refresh-token"))
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    @Test
    @DisplayName("이메일 또는 비밀번호가 일치하지 않으면 401을 반환한다")
    void login_invalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "wrongpassword1!");
        willThrow(new CustomException(AuthErrorCode.INVALID_CREDENTIALS))
                .given(authTokenService).login("user@example.com", "wrongpassword1!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_CREDENTIALS.getCode()));
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 400을 반환한다")
    void login_invalidEmailFormat() throws Exception {
        LoginRequest request = new LoginRequest("not-an-email", "abc12345!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.reasons.email").exists());
    }

    @Test
    @DisplayName("유효한 refreshToken 쿠키가 있으면 200과 새 accessToken을 반환하고 refreshToken 쿠키를 rotate한다")
    void reissue_success() throws Exception {
        given(authTokenService.reissue("refresh-token"))
                .willReturn(new ReissueResult(1L, "new-access-token", "new-refresh-token", MemberRole.USER));
        given(refreshTokenCookieFactory.create("new-refresh-token"))
                .willReturn(ResponseCookie.from("refreshToken", "new-refresh-token").httpOnly(true).build());

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(new Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(cookie().value("refreshToken", "new-refresh-token"));
    }

    @Test
    @DisplayName("refreshToken 쿠키가 없으면 401을 반환한다")
    void reissue_missingCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_REFRESH_TOKEN.getCode()));
    }

    @Test
    @DisplayName("refreshToken 쿠키 값이 비어있으면 500이 아니라 401을 반환한다")
    void reissue_blankCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(new Cookie("refreshToken", "")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_REFRESH_TOKEN.getCode()));

        org.mockito.Mockito.verify(authTokenService, org.mockito.Mockito.never()).reissue(anyString());
    }

    @Test
    @DisplayName("refreshToken이 만료됐으면 401을 반환한다")
    void reissue_expiredToken() throws Exception {
        willThrow(new CustomException(AuthErrorCode.REFRESH_TOKEN_EXPIRED))
                .given(authTokenService).reissue("refresh-token");

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(new Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.REFRESH_TOKEN_EXPIRED.getCode()));
    }

    @Test
    @DisplayName("refreshToken이 위변조됐거나 purpose가 다르면 401을 반환한다")
    void reissue_invalidToken() throws Exception {
        willThrow(new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN))
                .given(authTokenService).reissue("bad-token");

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .cookie(new Cookie("refreshToken", "bad-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_REFRESH_TOKEN.getCode()));
    }

    @Test
    @DisplayName("refreshToken 쿠키가 있으면 세션을 무효화하고 쿠키를 만료시킨다")
    void logout_withCookie_success() throws Exception {
        willDoNothing().given(authTokenService).logout("refresh-token");
        given(refreshTokenCookieFactory.createExpired())
                .willReturn(ResponseCookie.from("refreshToken", "").httpOnly(true).maxAge(0).build());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().maxAge("refreshToken", 0));

        org.mockito.Mockito.verify(authTokenService).logout("refresh-token");
    }

    @Test
    @DisplayName("refreshToken 쿠키 값이 비어있어도 500 없이 200을 반환한다")
    void logout_blankCookie_success() throws Exception {
        given(refreshTokenCookieFactory.createExpired())
                .willReturn(ResponseCookie.from("refreshToken", "").httpOnly(true).maxAge(0).build());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        org.mockito.Mockito.verify(authTokenService, org.mockito.Mockito.never()).logout(anyString());
    }

    @Test
    @DisplayName("refreshToken 쿠키가 없어도 에러 없이 200을 반환한다(멱등)")
    void logout_withoutCookie_success() throws Exception {
        given(refreshTokenCookieFactory.createExpired())
                .willReturn(ResponseCookie.from("refreshToken", "").httpOnly(true).maxAge(0).build());

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().maxAge("refreshToken", 0));

        org.mockito.Mockito.verify(authTokenService, org.mockito.Mockito.never()).logout(anyString());
    }

    @Test
    @DisplayName("가입된 로컬 계정이면 비밀번호 재설정 인증번호 발송 시 200을 반환한다")
    void sendPasswordResetVerificationCode_success() throws Exception {
        PasswordResetSendRequest request = new PasswordResetSendRequest("user@example.com");
        willDoNothing().given(emailVerificationCommandService).sendPasswordResetVerificationCode("user@example.com");

        mockMvc.perform(post("/api/v1/auth/password-reset/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("가입되지 않은 이메일로 비밀번호 재설정 인증번호를 요청하면 404를 반환한다")
    void sendPasswordResetVerificationCode_memberNotFound() throws Exception {
        PasswordResetSendRequest request = new PasswordResetSendRequest("user@example.com");
        willThrow(new CustomException(AuthErrorCode.MEMBER_NOT_FOUND))
                .given(emailVerificationCommandService).sendPasswordResetVerificationCode("user@example.com");

        mockMvc.perform(post("/api/v1/auth/password-reset/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.MEMBER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("소셜 로그인 전용 계정으로 비밀번호 재설정 인증번호를 요청하면 400을 반환한다")
    void sendPasswordResetVerificationCode_socialOnlyAccount() throws Exception {
        PasswordResetSendRequest request = new PasswordResetSendRequest("user@example.com");
        willThrow(new CustomException(AuthErrorCode.SOCIAL_ONLY_ACCOUNT))
                .given(emailVerificationCommandService).sendPasswordResetVerificationCode("user@example.com");

        mockMvc.perform(post("/api/v1/auth/password-reset/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.SOCIAL_ONLY_ACCOUNT.getCode()));
    }

    @Test
    @DisplayName("비밀번호 재설정 인증번호가 일치하면 200을 반환한다")
    void confirmPasswordResetVerificationCode_success() throws Exception {
        EmailVerificationConfirmRequest request = new EmailVerificationConfirmRequest("user@example.com", "123456");
        willDoNothing().given(emailVerificationCommandService).verifyPasswordResetCode("user@example.com", "123456");

        mockMvc.perform(post("/api/v1/auth/password-reset/email/verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("비밀번호 재설정 인증번호가 일치하지 않으면 400을 반환한다")
    void confirmPasswordResetVerificationCode_codeMismatch() throws Exception {
        EmailVerificationConfirmRequest request = new EmailVerificationConfirmRequest("user@example.com", "000000");
        willThrow(new CustomException(AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH))
                .given(emailVerificationCommandService).verifyPasswordResetCode("user@example.com", "000000");

        mockMvc.perform(post("/api/v1/auth/password-reset/email/verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH.getCode()));
    }

    @Test
    @DisplayName("정상 요청이면 비밀번호가 재설정되고 200을 반환한다")
    void resetPassword_success() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("user@example.com", "123456", "newPass12!", "newPass12!");
        willDoNothing().given(passwordResetCommandService)
                .resetPassword("user@example.com", "123456", "newPass12!", "newPass12!");

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("새 비밀번호와 확인이 다르면 400을 반환한다")
    void resetPassword_passwordConfirmationMismatch() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("user@example.com", "123456", "newPass12!", "different1!");
        willThrow(new CustomException(AuthErrorCode.PASSWORD_CONFIRMATION_MISMATCH))
                .given(passwordResetCommandService)
                .resetPassword("user@example.com", "123456", "newPass12!", "different1!");

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.PASSWORD_CONFIRMATION_MISMATCH.getCode()));
    }

    @Test
    @DisplayName("인증 완료 내역이 없으면 404를 반환한다")
    void resetPassword_verificationNotFound() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("user@example.com", "123456", "newPass12!", "newPass12!");
        willThrow(new CustomException(AuthErrorCode.EMAIL_VERIFICATION_NOT_FOUND))
                .given(passwordResetCommandService)
                .resetPassword("user@example.com", "123456", "newPass12!", "newPass12!");

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.EMAIL_VERIFICATION_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("재설정 시점에 인증번호가 만료됐으면 400을 반환한다")
    void resetPassword_verificationExpired() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("user@example.com", "123456", "newPass12!", "newPass12!");
        willThrow(new CustomException(AuthErrorCode.EMAIL_VERIFICATION_EXPIRED))
                .given(passwordResetCommandService)
                .resetPassword("user@example.com", "123456", "newPass12!", "newPass12!");

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.EMAIL_VERIFICATION_EXPIRED.getCode()));
    }

    @Test
    @DisplayName("새 비밀번호 형식이 올바르지 않으면 400을 반환한다")
    void resetPassword_invalidPasswordFormat() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("user@example.com", "123456", "short1!", "short1!");

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.reasons.newPassword").exists());
    }
}