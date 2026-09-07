package com.cotato.nextstation.domain.member.service;

import com.cotato.nextstation.domain.auth.client.AppleTokenClient;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.entity.SocialOauthCredential;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import com.cotato.nextstation.domain.member.repository.SocialOauthCredentialRepository;
import com.cotato.nextstation.global.security.OAuthRefreshTokenEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class WithdrawnMemberCleanerTest {

    @InjectMocks
    private WithdrawnMemberCleaner withdrawnMemberCleaner;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberSocialAccountRepository memberSocialAccountRepository;

    @Mock
    private SocialOauthCredentialRepository socialOauthCredentialRepository;

    @Mock
    private OAuthRefreshTokenEncryptor oAuthRefreshTokenEncryptor;

    @Mock
    private AppleTokenClient appleTokenClient;

    @Mock
    private WithdrawnMemberPurger withdrawnMemberPurger;

    private MemberSocialAccount appleAccount(Long memberId, Long accountId) {
        MemberSocialAccount account = MemberSocialAccount.builder()
                .memberId(memberId)
                .provider(AuthProvider.APPLE)
                .providerUserId("provider-user-" + memberId)
                .build();
        ReflectionTestUtils.setField(account, "id", accountId);
        return account;
    }

    private SocialOauthCredential credential(Long accountId, String encryptedRefreshToken) {
        return SocialOauthCredential.builder()
                .memberSocialAccountId(accountId)
                .provider(AuthProvider.APPLE)
                .refreshToken(encryptedRefreshToken)
                .build();
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 하지 않는다")
    void purge_noTargets() {
        // given
        given(memberRepository.findIdsByStatusAndDeletedAtBefore(eq(MemberStatus.WITHDRAWN), any()))
                .willReturn(List.of());

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        then(withdrawnMemberPurger).should(never()).purge(any());
    }

    @Test
    @DisplayName("Apple 연동이 없는 회원은 revoke 없이 그대로 파기 대상에 넘긴다")
    void purge_nonAppleMember_skipsRevoke() {
        // given
        given(memberRepository.findIdsByStatusAndDeletedAtBefore(eq(MemberStatus.WITHDRAWN), any()))
                .willReturn(List.of(1L));
        given(memberSocialAccountRepository.findByMemberIdInAndProvider(anyCollection(), eq(AuthProvider.APPLE)))
                .willReturn(List.of());

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        then(appleTokenClient).should(never()).revoke(any());
        then(withdrawnMemberPurger).should().purge(List.of(1L));
    }

    @Test
    @DisplayName("Apple refresh_token을 복호화해서 revoke를 호출하고, 성공하면 파기 대상에 그대로 남긴다")
    void purge_appleMemberWithCredential_revokesAndKeepsInPurgeTargets() {
        // given
        given(memberRepository.findIdsByStatusAndDeletedAtBefore(eq(MemberStatus.WITHDRAWN), any()))
                .willReturn(List.of(1L));
        given(memberSocialAccountRepository.findByMemberIdInAndProvider(anyCollection(), eq(AuthProvider.APPLE)))
                .willReturn(List.of(appleAccount(1L, 10L)));
        given(socialOauthCredentialRepository.findByMemberSocialAccountIdIn(anyCollection()))
                .willReturn(List.of(credential(10L, "encrypted")));
        given(oAuthRefreshTokenEncryptor.decrypt("encrypted")).willReturn("plain");
        given(appleTokenClient.revoke("plain")).willReturn(true);

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        then(appleTokenClient).should().revoke("plain");
        then(withdrawnMemberPurger).should().purge(List.of(1L));
    }

    @Test
    @DisplayName("revoke에 실패한 회원은 이번 파기 대상에서 제외한다 - 다음 배치가 같은 회원번호로 재시도한다")
    void purge_revokeFailure_excludesFromPurgeTargets() {
        // given
        given(memberRepository.findIdsByStatusAndDeletedAtBefore(eq(MemberStatus.WITHDRAWN), any()))
                .willReturn(List.of(1L, 2L));
        given(memberSocialAccountRepository.findByMemberIdInAndProvider(anyCollection(), eq(AuthProvider.APPLE)))
                .willReturn(List.of(appleAccount(1L, 10L)));
        given(socialOauthCredentialRepository.findByMemberSocialAccountIdIn(anyCollection()))
                .willReturn(List.of(credential(10L, "encrypted")));
        given(oAuthRefreshTokenEncryptor.decrypt("encrypted")).willReturn("plain");
        given(appleTokenClient.revoke("plain")).willReturn(false);

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then - memberId=1은 제외되고, Apple 연동이 없던 memberId=2만 파기된다
        then(withdrawnMemberPurger).should().purge(List.of(2L));
    }

    @Test
    @DisplayName("모두 revoke에 실패하면 이번 파기를 건너뛴다")
    void purge_allRevokeFailed_skipsPurge() {
        // given
        given(memberRepository.findIdsByStatusAndDeletedAtBefore(eq(MemberStatus.WITHDRAWN), any()))
                .willReturn(List.of(1L));
        given(memberSocialAccountRepository.findByMemberIdInAndProvider(anyCollection(), eq(AuthProvider.APPLE)))
                .willReturn(List.of(appleAccount(1L, 10L)));
        given(socialOauthCredentialRepository.findByMemberSocialAccountIdIn(anyCollection()))
                .willReturn(List.of(credential(10L, "encrypted")));
        given(oAuthRefreshTokenEncryptor.decrypt("encrypted")).willReturn("plain");
        given(appleTokenClient.revoke("plain")).willReturn(false);

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        then(withdrawnMemberPurger).should(never()).purge(any());
    }
}
