package com.cotato.nextstation.domain.auth.service.command;

import com.cotato.nextstation.domain.auth.client.AppleTokenClient;
import com.cotato.nextstation.domain.auth.client.dto.AppleTokenResponse;
import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.exception.TermsErrorCode;
import com.cotato.nextstation.domain.auth.repository.MemberTermsAgreementRepository;
import com.cotato.nextstation.domain.auth.util.AppleSignupTokenClaims;
import com.cotato.nextstation.domain.auth.util.TermsAgreementValidator;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.Gender;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import com.cotato.nextstation.domain.member.repository.SocialOauthCredentialRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.jwt.JwtProvider;
import com.cotato.nextstation.global.security.OAuthRefreshTokenEncryptor;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppleSignupCommandServiceTest {

    @InjectMocks
    private AppleSignupCommandService appleSignupCommandService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberSocialAccountRepository memberSocialAccountRepository;

    @Mock
    private MemberTermsAgreementRepository memberTermsAgreementRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private TermsAgreementValidator termsAgreementValidator;

    @Mock
    private SocialOauthCredentialRepository socialOauthCredentialRepository;

    @Mock
    private AppleTokenClient appleTokenClient;

    @Mock
    private OAuthRefreshTokenEncryptor oAuthRefreshTokenEncryptor;

    private static final String APPLE_SIGNUP_TOKEN = "apple-signup-token";
    private static final String AUTHORIZATION_CODE = "authorization-code";
    private static final String PROVIDER_USER_ID = "000555.abcdef1234567890.0555";

    private Claims validClaims(String email) {
        Claims claims = mock(Claims.class);
        given(claims.get(AppleSignupTokenClaims.PURPOSE_KEY, String.class)).willReturn(AppleSignupTokenClaims.APPLE_SIGNUP_PURPOSE);
        given(claims.getSubject()).willReturn(PROVIDER_USER_ID);
        given(claims.get(AppleSignupTokenClaims.EMAIL_KEY, String.class)).willReturn(email);
        return claims;
    }

    private Member savedMember() {
        Member member = Member.builder().email(null).build();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private Member pendingMember() {
        Member member = Member.builder().email(null).build();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private Member activeMember() {
        Member member = Member.builder().email(null).build();
        ReflectionTestUtils.setField(member, "id", 1L);
        member.completeProfile("기존닉네임", null, Gender.UNSPECIFIED, LocalDate.of(2000, 1, 1));
        return member;
    }

    private MemberSocialAccount socialAccount(Long memberId) {
        return MemberSocialAccount.builder()
                .memberId(memberId)
                .provider(AuthProvider.APPLE)
                .providerUserId(PROVIDER_USER_ID)
                .email(null)
                .build();
    }

    private MemberSocialAccount savedSocialAccount() {
        MemberSocialAccount socialAccount = socialAccount(1L);
        ReflectionTestUtils.setField(socialAccount, "id", 10L);
        return socialAccount;
    }

    @Test
    @DisplayName("정상 요청이면 Member와 MemberSocialAccount가 생성되고 약관 동의가 저장되고 signupToken이 발급된다")
    void signup_success() {
        // given
        Claims claims = validClaims("user@privaterelay.appleid.com");
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willReturn(claims);
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(savedMember());
        given(memberSocialAccountRepository.save(any(MemberSocialAccount.class))).willReturn(savedSocialAccount());
        given(jwtProvider.generateToken(eq("1"), any(Map.class), any(Duration.class))).willReturn("signup-token");

        // when
        var response = appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(1L), "127.0.0.1", AUTHORIZATION_CODE);

        // then
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.signupToken()).isEqualTo("signup-token");
        verify(memberSocialAccountRepository, times(1)).save(any(MemberSocialAccount.class));
        verify(memberTermsAgreementRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("authorizationCode 교환에 성공하면 refresh_token을 암호화해 저장한다")
    void signup_success_savesEncryptedOauthCredential() {
        // given
        Claims claims = validClaims("user@privaterelay.appleid.com");
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willReturn(claims);
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(savedMember());
        given(memberSocialAccountRepository.save(any(MemberSocialAccount.class))).willReturn(savedSocialAccount());
        given(jwtProvider.generateToken(eq("1"), any(Map.class), any(Duration.class))).willReturn("signup-token");
        given(appleTokenClient.exchangeAuthorizationCode(AUTHORIZATION_CODE))
                .willReturn(new AppleTokenResponse("access-token", "bearer", 3600L, "raw-refresh-token", "id-token"));
        given(oAuthRefreshTokenEncryptor.encrypt("raw-refresh-token")).willReturn("encrypted-refresh-token");

        // when
        appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(1L), "127.0.0.1", AUTHORIZATION_CODE);

        // then
        ArgumentCaptor<com.cotato.nextstation.domain.member.entity.SocialOauthCredential> captor =
                ArgumentCaptor.forClass(com.cotato.nextstation.domain.member.entity.SocialOauthCredential.class);
        verify(socialOauthCredentialRepository).save(captor.capture());
        assertThat(captor.getValue().getMemberSocialAccountId()).isEqualTo(10L);
        assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.APPLE);
        assertThat(captor.getValue().getRefreshToken()).isEqualTo("encrypted-refresh-token");
    }

    @Test
    @DisplayName("authorizationCode 교환에 실패해도(Apple Key 미발급 등) 가입 자체는 정상 처리된다")
    void signup_success_oauthCredentialExchangeFails() {
        // given
        Claims claims = validClaims("user@privaterelay.appleid.com");
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willReturn(claims);
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(savedMember());
        given(memberSocialAccountRepository.save(any(MemberSocialAccount.class))).willReturn(savedSocialAccount());
        given(jwtProvider.generateToken(eq("1"), any(Map.class), any(Duration.class))).willReturn("signup-token");
        willThrow(new IllegalStateException("apple.oauth.team-id/key-id/private-key가 설정되지 않았습니다."))
                .given(appleTokenClient).exchangeAuthorizationCode(AUTHORIZATION_CODE);

        // when
        var response = appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(1L), "127.0.0.1", AUTHORIZATION_CODE);

        // then
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.signupToken()).isEqualTo("signup-token");
        verify(socialOauthCredentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("Apple 이메일이 빈 문자열(미제공)이면 Member.email은 null로 저장된다")
    void signup_blankEmail_savedAsNull() {
        // given
        Claims claims = validClaims("");
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willReturn(claims);
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(savedMember());
        given(memberSocialAccountRepository.save(any(MemberSocialAccount.class))).willReturn(savedSocialAccount());
        given(jwtProvider.generateToken(eq("1"), any(Map.class), any(Duration.class))).willReturn("signup-token");

        // when
        appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(1L), "127.0.0.1", AUTHORIZATION_CODE);

        // then
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getEmail()).isNull();
    }

    @Test
    @DisplayName("Apple 인증 이메일이 기존 로컬 계정 이메일과 중복되면 예외가 발생한다")
    void signup_emailDuplicatedWithLocalAccount() {
        // given
        Claims claims = validClaims("user@example.com");
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willReturn(claims);
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.empty());
        given(memberRepository.existsByEmail("user@example.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(1L), "127.0.0.1", AUTHORIZATION_CODE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.DUPLICATE_EMAIL.getMessage());
        verify(memberRepository, never()).save(any());
        verify(memberSocialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 연동된(PENDING) Apple 계정이면 재가입 대신 signupToken만 재발급한다")
    void signup_reissueForExistingPendingMember() {
        // given
        Claims claims = validClaims("user@privaterelay.appleid.com");
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willReturn(claims);
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.of(socialAccount(1L)));
        given(memberRepository.findById(1L)).willReturn(Optional.of(pendingMember()));
        given(jwtProvider.generateToken(eq("1"), any(Map.class), any(Duration.class))).willReturn("reissued-token");

        // when
        var response = appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(1L), "127.0.0.1", AUTHORIZATION_CODE);

        // then
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.signupToken()).isEqualTo("reissued-token");
        verify(memberRepository, never()).save(any());
        verify(memberSocialAccountRepository, never()).save(any());
        verify(memberTermsAgreementRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("이미 프로필 설정까지 완료된(ACTIVE) Apple 계정으로 재가입 시도하면 예외가 발생한다")
    void signup_alreadyRegistered_activeMemberConflict() {
        // given
        Claims claims = validClaims("user@privaterelay.appleid.com");
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willReturn(claims);
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.of(socialAccount(1L)));
        given(memberRepository.findById(1L)).willReturn(Optional.of(activeMember()));

        // when & then
        assertThatThrownBy(() -> appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(1L), "127.0.0.1", AUTHORIZATION_CODE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.APPLE_ACCOUNT_ALREADY_REGISTERED.getMessage());
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("필수 약관을 동의하지 않으면 예외가 발생한다")
    void signup_requiredTermsNotAgreed() {
        // given
        Claims claims = validClaims("user@privaterelay.appleid.com");
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willReturn(claims);
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.empty());
        willThrow(new CustomException(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED))
                .given(termsAgreementValidator).validate(List.of());

        // when & then
        assertThatThrownBy(() -> appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(), "127.0.0.1", AUTHORIZATION_CODE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(TermsErrorCode.REQUIRED_TERMS_NOT_AGREED.getMessage());
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 약관 id가 포함되면 예외가 발생한다")
    void signup_termsNotFound() {
        // given
        Claims claims = validClaims("user@privaterelay.appleid.com");
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willReturn(claims);
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.empty());
        willThrow(new CustomException(TermsErrorCode.TERMS_NOT_FOUND))
                .given(termsAgreementValidator).validate(List.of(1L, 999L));

        // when & then
        assertThatThrownBy(() -> appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(1L, 999L), "127.0.0.1", AUTHORIZATION_CODE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(TermsErrorCode.TERMS_NOT_FOUND.getMessage());
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("만료된 appleSignupToken이면 예외가 발생한다")
    void signup_expiredAppleSignupToken() {
        // given
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willThrow(new ExpiredJwtException(null, null, "expired"));

        // when & then
        assertThatThrownBy(() -> appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(1L), "127.0.0.1", AUTHORIZATION_CODE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.APPLE_SIGNUP_TOKEN_EXPIRED.getMessage());
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("위변조된 appleSignupToken이면 예외가 발생한다")
    void signup_invalidSignatureToken() {
        // given
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willThrow(new JwtException("invalid signature"));

        // when & then
        assertThatThrownBy(() -> appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(1L), "127.0.0.1", AUTHORIZATION_CODE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.INVALID_APPLE_SIGNUP_TOKEN.getMessage());
    }

    @Test
    @DisplayName("purpose가 APPLE_SIGNUP이 아니면 예외가 발생한다 (다른 토큰을 잘못 넣은 경우)")
    void signup_wrongPurposeToken() {
        // given
        Claims claims = mock(Claims.class);
        given(claims.get(AppleSignupTokenClaims.PURPOSE_KEY, String.class)).willReturn("SIGNUP");
        given(jwtProvider.parseClaims(APPLE_SIGNUP_TOKEN)).willReturn(claims);

        // when & then
        assertThatThrownBy(() -> appleSignupCommandService.signup(APPLE_SIGNUP_TOKEN, List.of(1L), "127.0.0.1", AUTHORIZATION_CODE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.INVALID_APPLE_SIGNUP_TOKEN.getMessage());
    }
}
