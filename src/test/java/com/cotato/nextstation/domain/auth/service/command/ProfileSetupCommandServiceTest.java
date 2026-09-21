package com.cotato.nextstation.domain.auth.service.command;

import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.service.AuthTokenIssuer;
import com.cotato.nextstation.domain.auth.service.IssuedTokens;
import com.cotato.nextstation.domain.auth.service.result.ProfileSetupResult;
import com.cotato.nextstation.domain.member.entity.Gender;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.exception.NicknameErrorCode;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.exception.MemberErrorCode;
import com.cotato.nextstation.domain.member.util.NicknameValidator;
import com.cotato.nextstation.domain.member.util.ProfileImageUrlValidator;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ProfileSetupCommandServiceTest {

    private ProfileSetupCommandService profileSetupCommandService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private NicknameValidator nicknameValidator;

    @Mock
    private ProfileImageUrlValidator profileImageUrlValidator;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AuthTokenIssuer authTokenIssuer;

    private static final Long MEMBER_ID = 1L;
    private static final String NICKNAME = "환승러";
    private static final Gender GENDER = Gender.MALE;
    private static final LocalDate BIRTH_DATE = LocalDate.of(2001, 1, 1);
    private static final String TOKEN = "signup-token";
    private static final String AUTH_HEADER = "Bearer " + TOKEN;
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String VALID_PROFILE_IMAGE_URL =
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/uploads/profile/1/uuid.jpg";

    @BeforeEach
    void setUp() {
        profileSetupCommandService = new ProfileSetupCommandService(
                memberRepository, nicknameValidator, profileImageUrlValidator, jwtProvider, authTokenIssuer);
    }

    private Member pendingMember() {
        Member member = Member.builder().email("user@example.com").password("encoded").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        return member;
    }

    private Claims signupClaims(Long memberId) {
        return Jwts.claims().subject(memberId.toString()).add("purpose", "SIGNUP").build();
    }

    private void givenValidToken() {
        given(jwtProvider.parseClaims(TOKEN)).willReturn(signupClaims(MEMBER_ID));
    }

    @Test
    @DisplayName("정상 요청이면 프로필이 설정되고 status가 ACTIVE로 전환된다")
    void setupProfile_success() {
        // given
        givenValidToken();
        Member member = pendingMember();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(memberRepository.activateIfPending(MEMBER_ID)).willReturn(1);
        given(authTokenIssuer.issue(MEMBER_ID)).willReturn(new IssuedTokens(ACCESS_TOKEN, REFRESH_TOKEN));

        // when
        ProfileSetupResult result = profileSetupCommandService.setupProfile(
                AUTH_HEADER, NICKNAME, VALID_PROFILE_IMAGE_URL, GENDER, BIRTH_DATE);

        // then
        assertThat(result.memberId()).isEqualTo(MEMBER_ID);
        assertThat(result.nickname()).isEqualTo(NICKNAME);
        assertThat(result.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getGender()).isEqualTo(GENDER);
        assertThat(member.getBirthDate()).isEqualTo(BIRTH_DATE);
        assertThat(member.getProfileImageUrl()).isEqualTo(VALID_PROFILE_IMAGE_URL);
    }

    @Test
    @DisplayName("프로필 설정에 성공하면 재로그인 없이 쓸 수 있도록 access/refresh token이 함께 발급된다")
    void setupProfile_issuesTokens() {
        // given
        givenValidToken();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(pendingMember()));
        given(memberRepository.activateIfPending(MEMBER_ID)).willReturn(1);
        given(authTokenIssuer.issue(MEMBER_ID)).willReturn(new IssuedTokens(ACCESS_TOKEN, REFRESH_TOKEN));

        // when
        ProfileSetupResult result = profileSetupCommandService.setupProfile(
                AUTH_HEADER, NICKNAME, null, GENDER, BIRTH_DATE);

        // then
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
    }

    // 조회 시점엔 PENDING이었지만 그 사이 다른 요청이 전환을 끝낸 경우 - 조건부 갱신이 0행을 반환한다.
    // 이 요청까지 통과하면 프로필이 덮어써지고 로그인 세션도 중복 발급된다.
    @Test
    @DisplayName("상태 전환 선점에 실패하면(동시 요청) 예외가 발생하고 토큰이 발급되지 않는다")
    void setupProfile_concurrentRequest_losesClaim() {
        // given
        givenValidToken();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(pendingMember()));
        given(memberRepository.activateIfPending(MEMBER_ID)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> profileSetupCommandService.setupProfile(
                AUTH_HEADER, "다른닉네임", null, GENDER, BIRTH_DATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.PROFILE_ALREADY_COMPLETED.getMessage());
        then(authTokenIssuer).shouldHaveNoInteractions();
        then(memberRepository).should(never()).saveAndFlush(any(Member.class));
    }

    // 프로필 설정에 실패한 요청까지 세션을 열면 PENDING 회원이 로그인 상태가 되므로, 발급 자체가 일어나지 않아야 한다.
    @Test
    @DisplayName("이미 프로필 설정이 완료된 회원의 재요청에는 토큰이 발급되지 않는다")
    void setupProfile_alreadyCompleted_doesNotIssueTokens() {
        // given
        givenValidToken();
        Member member = pendingMember();
        member.completeProfile("기존닉네임", null, Gender.UNSPECIFIED, BIRTH_DATE);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> profileSetupCommandService.setupProfile(AUTH_HEADER, NICKNAME, null, GENDER, BIRTH_DATE))
                .isInstanceOf(CustomException.class);
        then(authTokenIssuer).shouldHaveNoInteractions();
    }

    // 프로필 이미지 URL 자체의 검증 규칙은 ProfileImageUrlValidator가 담당하므로,
    // 여기서는 검증 실패 시 그 예외가 그대로 전파되는지만 확인한다.
    @Test
    @DisplayName("프로필 이미지 URL 검증에서 예외가 발생하면 그대로 전파된다")
    void setupProfile_invalidProfileImageUrl() {
        // given
        givenValidToken();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(pendingMember()));
        doThrow(new CustomException(MemberErrorCode.INVALID_PROFILE_IMAGE_URL))
                .when(profileImageUrlValidator).validate("https://evil.com/xss.svg", MEMBER_ID);

        // when & then
        assertThatThrownBy(() -> profileSetupCommandService.setupProfile(
                AUTH_HEADER, NICKNAME, "https://evil.com/xss.svg", GENDER, BIRTH_DATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberErrorCode.INVALID_PROFILE_IMAGE_URL.getMessage());
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 예외가 발생한다")
    void setupProfile_missingAuthorizationHeader() {
        assertThatThrownBy(() -> profileSetupCommandService.setupProfile(null, NICKNAME, null, GENDER, BIRTH_DATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.INVALID_SIGNUP_TOKEN.getMessage());
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 예외가 발생한다")
    void setupProfile_notBearerFormat() {
        assertThatThrownBy(() -> profileSetupCommandService.setupProfile(TOKEN, NICKNAME, null, GENDER, BIRTH_DATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.INVALID_SIGNUP_TOKEN.getMessage());
    }

    @Test
    @DisplayName("만료된 토큰이면 예외가 발생한다")
    void setupProfile_expiredToken() {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willThrow(mock(ExpiredJwtException.class));

        // when & then
        assertThatThrownBy(() -> profileSetupCommandService.setupProfile(AUTH_HEADER, NICKNAME, null, GENDER, BIRTH_DATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.SIGNUP_TOKEN_EXPIRED.getMessage());
    }

    @Test
    @DisplayName("위변조된 토큰이면 예외가 발생한다")
    void setupProfile_malformedToken() {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willThrow(new MalformedJwtException("malformed"));

        // when & then
        assertThatThrownBy(() -> profileSetupCommandService.setupProfile(AUTH_HEADER, NICKNAME, null, GENDER, BIRTH_DATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.INVALID_SIGNUP_TOKEN.getMessage());
    }

    @Test
    @DisplayName("purpose가 SIGNUP이 아니면 예외가 발생한다")
    void setupProfile_wrongPurpose() {
        // given
        Claims wrongPurposeClaims = Jwts.claims().subject(MEMBER_ID.toString()).add("purpose", "ACCESS").build();
        given(jwtProvider.parseClaims(TOKEN)).willReturn(wrongPurposeClaims);

        // when & then
        assertThatThrownBy(() -> profileSetupCommandService.setupProfile(AUTH_HEADER, NICKNAME, null, GENDER, BIRTH_DATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.INVALID_SIGNUP_TOKEN.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 예외가 발생한다")
    void setupProfile_memberNotFound() {
        // given
        givenValidToken();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> profileSetupCommandService.setupProfile(AUTH_HEADER, NICKNAME, null, GENDER, BIRTH_DATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("이미 프로필 설정이 완료된 회원(status != PENDING)이면 예외가 발생한다")
    void setupProfile_alreadyCompleted() {
        // given
        givenValidToken();
        Member member = pendingMember();
        member.completeProfile("기존닉네임", null, Gender.UNSPECIFIED, BIRTH_DATE);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> profileSetupCommandService.setupProfile(AUTH_HEADER, NICKNAME, null, GENDER, BIRTH_DATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.PROFILE_ALREADY_COMPLETED.getMessage());
    }

    // 닉네임 자체의 검증 규칙(길이/문자/금칙어/예약어/중복)은 NicknameValidatorTest에서 다루고,
    // 여기서는 검증 실패 시 그 예외가 그대로 전파되는지만 확인한다.
    @Test
    @DisplayName("닉네임 검증에서 예외가 발생하면 그대로 전파된다")
    void setupProfile_nicknameValidationFails() {
        // given
        givenValidToken();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(pendingMember()));
        doThrow(new CustomException(NicknameErrorCode.DUPLICATE_NICKNAME))
                .when(nicknameValidator).validate(NICKNAME);

        // when & then
        assertThatThrownBy(() -> profileSetupCommandService.setupProfile(AUTH_HEADER, NICKNAME, null, GENDER, BIRTH_DATE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(NicknameErrorCode.DUPLICATE_NICKNAME.getMessage());
    }
}