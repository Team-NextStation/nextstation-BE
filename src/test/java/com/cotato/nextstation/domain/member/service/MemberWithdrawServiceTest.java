package com.cotato.nextstation.domain.member.service;

import com.cotato.nextstation.domain.auth.client.AppleTokenClient;
import com.cotato.nextstation.domain.auth.repository.RefreshSessionRepository;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.entity.SocialOauthCredential;
import com.cotato.nextstation.domain.member.exception.MemberErrorCode;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import com.cotato.nextstation.domain.member.repository.SocialOauthCredentialRepository;
import com.cotato.nextstation.domain.member.service.command.MemberCommandService;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.security.OAuthRefreshTokenEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawServiceTest {

    @InjectMocks
    private MemberWithdrawService memberWithdrawService;

    @Mock
    private MemberCommandService memberCommandService;

    @Mock
    private RefreshSessionRepository refreshSessionRepository;

    @Mock
    private MemberSocialAccountRepository memberSocialAccountRepository;

    @Mock
    private SocialOauthCredentialRepository socialOauthCredentialRepository;

    @Mock
    private OAuthRefreshTokenEncryptor oAuthRefreshTokenEncryptor;

    @Mock
    private AppleTokenClient appleTokenClient;

    private MemberSocialAccount appleSocialAccount() {
        MemberSocialAccount socialAccount = MemberSocialAccount.builder()
                .memberId(1L)
                .provider(AuthProvider.APPLE)
                .providerUserId("000555.abcdef1234567890.0555")
                .build();
        ReflectionTestUtils.setField(socialAccount, "id", 10L);
        return socialAccount;
    }

    private SocialOauthCredential credential(String encryptedRefreshToken) {
        return SocialOauthCredential.builder()
                .memberSocialAccountId(10L)
                .provider(AuthProvider.APPLE)
                .refreshToken(encryptedRefreshToken)
                .build();
    }

    @Test
    @DisplayName("DB 처리를 마친 뒤에 세션을 삭제한다")
    void withdraw_dbBeforeRedis() {
        // when
        memberWithdrawService.withdraw(1L);

        // then
        InOrder inOrder = inOrder(memberCommandService, refreshSessionRepository);
        inOrder.verify(memberCommandService).withdraw(1L);
        inOrder.verify(refreshSessionRepository).deleteAllOf(1L);
    }

    @Test
    @DisplayName("세션 삭제가 실패해도 예외를 올리지 않는다 - 탈퇴는 이미 커밋됐으므로 실패 응답을 주면 안 된다")
    void withdraw_swallowsSessionDeletionFailure() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(refreshSessionRepository).deleteAllOf(1L);

        // when & then
        assertThatCode(() -> memberWithdrawService.withdraw(1L))
                .doesNotThrowAnyException();

        then(memberCommandService).should().withdraw(1L);
    }

    @Test
    @DisplayName("DB 처리가 실패하면 세션을 지우지 않는다 - 탈퇴는 안 됐는데 로그아웃만 되는 상태를 막는다")
    void withdraw_keepsSessionsWhenDbFails() {
        // given
        willThrow(new CustomException(MemberErrorCode.MEMBER_NOT_FOUND))
                .given(memberCommandService).withdraw(1L);

        // when & then
        assertThatThrownBy(() -> memberWithdrawService.withdraw(1L))
                .isInstanceOf(CustomException.class);

        then(refreshSessionRepository).should(never()).deleteAllOf(1L);
    }

    @Test
    @DisplayName("로컬/카카오 회원이면 Apple revoke를 시도하지 않는다")
    void withdraw_nonAppleMember_skipsRevoke() {
        // given
        given(memberSocialAccountRepository.findFirstByMemberIdOrderByIdAsc(1L)).willReturn(Optional.empty());

        // when
        memberWithdrawService.withdraw(1L);

        // then
        then(appleTokenClient).should(never()).revoke(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Apple 회원인데 저장된 refresh_token이 없으면 revoke를 시도하지 않는다")
    void withdraw_appleMemberWithoutCredential_skipsRevoke() {
        // given
        given(memberSocialAccountRepository.findFirstByMemberIdOrderByIdAsc(1L)).willReturn(Optional.of(appleSocialAccount()));
        given(socialOauthCredentialRepository.findByMemberSocialAccountId(10L)).willReturn(Optional.empty());

        // when
        memberWithdrawService.withdraw(1L);

        // then
        then(appleTokenClient).should(never()).revoke(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Apple 회원이고 refresh_token이 저장되어 있으면 복호화해서 revoke를 호출한다")
    void withdraw_appleMemberWithCredential_revokesToken() {
        // given
        given(memberSocialAccountRepository.findFirstByMemberIdOrderByIdAsc(1L)).willReturn(Optional.of(appleSocialAccount()));
        given(socialOauthCredentialRepository.findByMemberSocialAccountId(10L))
                .willReturn(Optional.of(credential("encrypted-refresh-token")));
        given(oAuthRefreshTokenEncryptor.decrypt("encrypted-refresh-token")).willReturn("plain-refresh-token");

        // when
        memberWithdrawService.withdraw(1L);

        // then
        then(appleTokenClient).should().revoke("plain-refresh-token");
    }

    @Test
    @DisplayName("Apple revoke 호출이 실패해도 예외를 올리지 않는다 - 탈퇴는 이미 완료된 상태다")
    void withdraw_swallowsRevokeFailure() {
        // given
        given(memberSocialAccountRepository.findFirstByMemberIdOrderByIdAsc(1L)).willReturn(Optional.of(appleSocialAccount()));
        given(socialOauthCredentialRepository.findByMemberSocialAccountId(10L))
                .willReturn(Optional.of(credential("encrypted-refresh-token")));
        given(oAuthRefreshTokenEncryptor.decrypt("encrypted-refresh-token")).willReturn("plain-refresh-token");
        willThrow(new RuntimeException("Apple 통신 실패"))
                .given(appleTokenClient).revoke("plain-refresh-token");

        // when & then
        assertThatCode(() -> memberWithdrawService.withdraw(1L))
                .doesNotThrowAnyException();
    }
}
