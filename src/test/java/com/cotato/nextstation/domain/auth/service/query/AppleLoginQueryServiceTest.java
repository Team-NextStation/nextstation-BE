package com.cotato.nextstation.domain.auth.service.query;

import com.cotato.nextstation.domain.auth.client.AppleOAuthClient;
import com.cotato.nextstation.domain.auth.client.dto.AppleIdentityToken;
import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.service.AuthTokenIssuer;
import com.cotato.nextstation.domain.auth.service.IssuedTokens;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResult;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResultType;
import com.cotato.nextstation.domain.auth.util.AppleSignupTokenClaims;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.Gender;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import com.cotato.nextstation.domain.member.service.command.MemberCommandService;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.jwt.JwtProvider;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AppleLoginQueryServiceTest {

    @InjectMocks
    private AppleLoginQueryService appleLoginQueryService;

    @Mock
    private AppleOAuthClient appleOAuthClient;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberSocialAccountRepository memberSocialAccountRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AuthTokenIssuer authTokenIssuer;

    @Mock
    private MemberCommandService memberCommandService;

    private static final String IDENTITY_TOKEN = "identity-token";
    private static final String PROVIDER_USER_ID = "000555.abcdef1234567890.0555";

    private AppleIdentityToken identityTokenWithEmail() {
        return new AppleIdentityToken(PROVIDER_USER_ID, "user@privaterelay.appleid.com");
    }

    private AppleIdentityToken identityTokenWithoutEmail() {
        // 재로그인 시(email_verified 클레임 자체가 안 내려오는 경우) email이 null인 케이스
        return new AppleIdentityToken(PROVIDER_USER_ID, null);
    }

    private Member pendingMember() {
        Member member = Member.builder().email(null).build();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private Member activeMember() {
        Member member = Member.builder().email(null).build();
        ReflectionTestUtils.setField(member, "id", 1L);
        member.completeProfile("환승러", null, Gender.UNSPECIFIED, LocalDate.of(2000, 1, 1));
        return member;
    }

    private Member withdrawnMember(LocalDateTime deletedAt) {
        Member member = activeMember();
        member.withdraw();
        ReflectionTestUtils.setField(member, "deletedAt", deletedAt);
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

    @Test
    @DisplayName("처음 보는 Apple 계정이면 Member를 만들지 않고 appleSignupToken을 발급한다")
    void login_newMember() {
        // given
        given(appleOAuthClient.verify(IDENTITY_TOKEN)).willReturn(identityTokenWithEmail());
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.empty());
        given(jwtProvider.generateToken(eq(PROVIDER_USER_ID), any(Map.class), any(Duration.class)))
                .willReturn("apple-signup-token");

        // when
        AppleLoginResult result = appleLoginQueryService.login(IDENTITY_TOKEN);

        // then
        assertThat(result.resultType()).isEqualTo(AppleLoginResultType.NEW_MEMBER);
        assertThat(result.appleSignupToken()).isEqualTo("apple-signup-token");
        assertThat(result.memberId()).isNull();
        assertThat(result.accessToken()).isNull();
    }

    @Test
    @DisplayName("email이 없는(재인증 등으로 클레임이 비어있는) 신규 회원이어도 NPE 없이 appleSignupToken을 발급한다")
    void login_newMember_noEmail() {
        // given
        given(appleOAuthClient.verify(IDENTITY_TOKEN)).willReturn(identityTokenWithoutEmail());
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.empty());
        given(jwtProvider.generateToken(eq(PROVIDER_USER_ID), any(Map.class), any(Duration.class)))
                .willReturn("apple-signup-token");

        // when
        AppleLoginResult result = appleLoginQueryService.login(IDENTITY_TOKEN);

        // then
        assertThat(result.resultType()).isEqualTo(AppleLoginResultType.NEW_MEMBER);

        // Map.of()는 value가 null이면 NPE를 던지므로, claim에는 빈 문자열로 들어갔는지 확인
        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(jwtProvider).generateToken(eq(PROVIDER_USER_ID), claimsCaptor.capture(), any(Duration.class));
        assertThat(claimsCaptor.getValue().get(AppleSignupTokenClaims.EMAIL_KEY)).isEqualTo("");
    }

    @Test
    @DisplayName("프로필 설정이 끝나지 않은(PENDING) Apple 회원이 재로그인하면 signupToken을 재발급한다")
    void login_pendingMember() {
        // given
        given(appleOAuthClient.verify(IDENTITY_TOKEN)).willReturn(identityTokenWithEmail());
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.of(socialAccount(1L)));
        given(memberRepository.findById(1L)).willReturn(Optional.of(pendingMember()));
        given(jwtProvider.generateToken(eq("1"), any(Map.class), any(Duration.class)))
                .willReturn("reissued-signup-token");

        // when
        AppleLoginResult result = appleLoginQueryService.login(IDENTITY_TOKEN);

        // then
        assertThat(result.resultType()).isEqualTo(AppleLoginResultType.PENDING_PROFILE);
        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.signupToken()).isEqualTo("reissued-signup-token");
    }

    @Test
    @DisplayName("ACTIVE Apple 회원이 로그인하면 access token과 refresh token을 발급한다")
    void login_activeMember_loginSuccess() {
        // given
        given(appleOAuthClient.verify(IDENTITY_TOKEN)).willReturn(identityTokenWithEmail());
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.of(socialAccount(1L)));
        given(memberRepository.findById(1L)).willReturn(Optional.of(activeMember()));
        given(authTokenIssuer.issue(1L)).willReturn(new IssuedTokens("access-token", "refresh-token"));

        // when
        AppleLoginResult result = appleLoginQueryService.login(IDENTITY_TOKEN);

        // then
        assertThat(result.resultType()).isEqualTo(AppleLoginResultType.LOGIN_SUCCESS);
        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("유예 기간이 지난 탈퇴 회원이면 복구하지 않고 예외가 발생한다")
    void login_memberNotActive() {
        // given - 8일 전 탈퇴 (유예 7일 경과)
        given(appleOAuthClient.verify(IDENTITY_TOKEN)).willReturn(identityTokenWithEmail());
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.of(socialAccount(1L)));
        given(memberRepository.findById(1L)).willReturn(Optional.of(withdrawnMember(LocalDateTime.now().minusDays(8))));

        // when & then
        assertThatThrownBy(() -> appleLoginQueryService.login(IDENTITY_TOKEN))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.APPLE_MEMBER_NOT_ACTIVE.getMessage());

        then(memberCommandService).should(never()).restore(any());
    }

    @Test
    @DisplayName("유예 기간이 남은 탈퇴 회원이 Apple로 로그인하면 계정을 복구하고 토큰을 발급한다")
    void login_restoresWithdrawnMemberWithinGracePeriod() {
        // given - 3일 전 탈퇴
        given(appleOAuthClient.verify(IDENTITY_TOKEN)).willReturn(identityTokenWithEmail());
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.of(socialAccount(1L)));
        given(memberRepository.findById(1L)).willReturn(Optional.of(withdrawnMember(LocalDateTime.now().minusDays(3))));
        given(memberCommandService.restore(1L)).willReturn(MemberStatus.ACTIVE);
        given(authTokenIssuer.issue(1L)).willReturn(new IssuedTokens("access-token", "refresh-token"));

        // when
        AppleLoginResult result = appleLoginQueryService.login(IDENTITY_TOKEN);

        // then
        assertThat(result.resultType()).isEqualTo(AppleLoginResultType.LOGIN_SUCCESS);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.restored()).isTrue();
    }

    @Test
    @DisplayName("member_social_account는 있는데 member가 없으면(데이터 정합성 오류) 예외가 발생한다")
    void login_memberNotFound_dataIntegrityError() {
        // given
        given(appleOAuthClient.verify(IDENTITY_TOKEN)).willReturn(identityTokenWithEmail());
        given(memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, PROVIDER_USER_ID))
                .willReturn(Optional.of(socialAccount(999L)));
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> appleLoginQueryService.login(IDENTITY_TOKEN))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(AuthErrorCode.MEMBER_NOT_FOUND.getMessage());
    }
}
