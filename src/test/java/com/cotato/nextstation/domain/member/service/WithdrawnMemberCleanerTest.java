package com.cotato.nextstation.domain.member.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cotato.nextstation.domain.auth.client.AppleTokenClient;
import com.cotato.nextstation.domain.auth.client.KakaoOAuthClient;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.entity.SocialOauthCredential;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import com.cotato.nextstation.domain.member.repository.SocialOauthCredentialRepository;
import com.cotato.nextstation.global.security.OAuthRefreshTokenEncryptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private WithdrawnMemberPurger withdrawnMemberPurger;

    private Logger logger;
    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void setUpLogCapture() {
        logCapture = new ListAppender<>();
        logCapture.start();
        logger = (Logger) LoggerFactory.getLogger(WithdrawnMemberCleaner.class);
        logger.addAppender(logCapture);
    }

    @AfterEach
    void tearDownLogCapture() {
        logger.detachAppender(logCapture);
    }

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

    private MemberSocialAccount kakaoAccount(Long memberId, String providerUserId) {
        return MemberSocialAccount.builder()
                .memberId(memberId)
                .provider(AuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .build();
    }

    private void givenTargets(List<Long> memberIds) {
        given(memberRepository.findIdsByStatusAndDeletedAtBefore(eq(MemberStatus.WITHDRAWN), any()))
                .willReturn(memberIds);
    }

    private void givenAppleAccounts(List<MemberSocialAccount> accounts) {
        given(memberSocialAccountRepository.findByMemberIdInAndProvider(anyCollection(), eq(AuthProvider.APPLE)))
                .willReturn(accounts);
    }

    private void givenKakaoAccounts(List<MemberSocialAccount> accounts) {
        given(memberSocialAccountRepository.findByMemberIdInAndProvider(anyCollection(), eq(AuthProvider.KAKAO)))
                .willReturn(accounts);
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 하지 않는다")
    void purge_noTargets() {
        // given
        givenTargets(List.of());

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        then(withdrawnMemberPurger).should(never()).purge(any());
        then(appleTokenClient).shouldHaveNoInteractions();
        then(kakaoOAuthClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소셜 연동이 없는 회원은 해제 없이 그대로 파기 대상에 넘긴다")
    void purge_localMembers_skipsRevoke() {
        // given
        givenTargets(List.of(1L));
        givenAppleAccounts(List.of());
        givenKakaoAccounts(List.of());

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        then(appleTokenClient).should(never()).revoke(any());
        then(kakaoOAuthClient).should(never()).unlink(any());
        then(withdrawnMemberPurger).should().purge(List.of(1L));
    }

    @Test
    @DisplayName("Apple refresh_token을 복호화해서 revoke를 호출하고, 성공하면 파기 대상에 그대로 남긴다")
    void purge_appleMemberWithCredential_revokesAndKeepsInPurgeTargets() {
        // given
        givenTargets(List.of(1L));
        givenAppleAccounts(List.of(appleAccount(1L, 10L)));
        givenKakaoAccounts(List.of());
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
    @DisplayName("Apple revoke에 실패한 회원은 이번 파기 대상에서 제외한다 - 다음 배치가 같은 회원번호로 재시도한다")
    void purge_appleRevokeFailure_excludesFromPurgeTargets() {
        // given
        givenTargets(List.of(1L, 2L));
        givenAppleAccounts(List.of(appleAccount(1L, 10L)));
        givenKakaoAccounts(List.of());
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
    @DisplayName("Apple 시도 대상 중 일부만 실패하면 WARN으로 남긴다")
    void purge_applePartialFailure_logsWarn() {
        // given
        givenTargets(List.of(1L, 2L));
        givenAppleAccounts(List.of(appleAccount(1L, 10L), appleAccount(2L, 20L)));
        givenKakaoAccounts(List.of());
        given(socialOauthCredentialRepository.findByMemberSocialAccountIdIn(anyCollection()))
                .willReturn(List.of(credential(10L, "encrypted-1"), credential(20L, "encrypted-2")));
        given(oAuthRefreshTokenEncryptor.decrypt("encrypted-1")).willReturn("plain-1");
        given(oAuthRefreshTokenEncryptor.decrypt("encrypted-2")).willReturn("plain-2");
        given(appleTokenClient.revoke("plain-1")).willReturn(false);
        given(appleTokenClient.revoke("plain-2")).willReturn(true);

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        assertThat(logCapture.list).extracting(ILoggingEvent::getLevel).contains(Level.WARN).doesNotContain(Level.ERROR);
    }

    @Test
    @DisplayName("Apple 시도 대상 전원이 실패하면 설정 문제로 보고 ERROR로 남긴다")
    void purge_appleAllFailure_logsError() {
        // given
        givenTargets(List.of(1L, 2L));
        givenAppleAccounts(List.of(appleAccount(1L, 10L), appleAccount(2L, 20L)));
        givenKakaoAccounts(List.of());
        given(socialOauthCredentialRepository.findByMemberSocialAccountIdIn(anyCollection()))
                .willReturn(List.of(credential(10L, "encrypted-1"), credential(20L, "encrypted-2")));
        given(oAuthRefreshTokenEncryptor.decrypt("encrypted-1")).willReturn("plain-1");
        given(oAuthRefreshTokenEncryptor.decrypt("encrypted-2")).willReturn("plain-2");
        given(appleTokenClient.revoke("plain-1")).willReturn(false);
        given(appleTokenClient.revoke("plain-2")).willReturn(false);

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        assertThat(logCapture.list).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
    }

    @Test
    @DisplayName("카카오 연결을 해제한 뒤 파기한다")
    void purge_kakaoUnlinksBeforeDelete() {
        // given
        givenTargets(List.of(1L, 2L));
        givenAppleAccounts(List.of());
        givenKakaoAccounts(List.of(kakaoAccount(1L, "kakao-1"), kakaoAccount(2L, "kakao-2")));
        given(kakaoOAuthClient.unlink("kakao-1")).willReturn(true);
        given(kakaoOAuthClient.unlink("kakao-2")).willReturn(true);

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        then(kakaoOAuthClient).should().unlink("kakao-1");
        then(kakaoOAuthClient).should().unlink("kakao-2");
        then(withdrawnMemberPurger).should().purge(List.of(1L, 2L));
    }

    @Test
    @DisplayName("카카오 연결 해제에 실패한 회원만 파기 대상에서 빠진다")
    void purge_kakaoUnlinkFailure_excludesFromPurgeTargets() {
        // given
        givenTargets(List.of(1L, 2L, 3L));
        givenAppleAccounts(List.of());
        givenKakaoAccounts(List.of(kakaoAccount(1L, "kakao-1"), kakaoAccount(2L, "kakao-2")));
        given(kakaoOAuthClient.unlink("kakao-1")).willReturn(true);
        given(kakaoOAuthClient.unlink("kakao-2")).willReturn(false);

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then - 실패한 2번만 빠지고, 소셜 연동이 없는 3번은 그대로 파기된다
        then(withdrawnMemberPurger).should().purge(List.of(1L, 3L));
    }

    @Test
    @DisplayName("Apple/카카오 중 하나라도 해제 실패한 회원이 있으면 그 회원만 빠지고 나머지는 함께 파기된다")
    void purge_mixedProviders_excludesOnlyFailedOnes() {
        // given
        givenTargets(List.of(1L, 2L));
        givenAppleAccounts(List.of(appleAccount(1L, 10L)));
        givenKakaoAccounts(List.of(kakaoAccount(2L, "kakao-2")));
        given(socialOauthCredentialRepository.findByMemberSocialAccountIdIn(anyCollection()))
                .willReturn(List.of(credential(10L, "encrypted")));
        given(oAuthRefreshTokenEncryptor.decrypt("encrypted")).willReturn("plain");
        given(appleTokenClient.revoke("plain")).willReturn(true);
        given(kakaoOAuthClient.unlink("kakao-2")).willReturn(true);

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        then(withdrawnMemberPurger).should().purge(List.of(1L, 2L));
    }

    @Test
    @DisplayName("모두 해제에 실패하면 이번 파기를 건너뛴다")
    void purge_allFailed_skipsPurge() {
        // given
        givenTargets(List.of(1L));
        givenAppleAccounts(List.of(appleAccount(1L, 10L)));
        givenKakaoAccounts(List.of());
        given(socialOauthCredentialRepository.findByMemberSocialAccountIdIn(anyCollection()))
                .willReturn(List.of(credential(10L, "encrypted")));
        given(oAuthRefreshTokenEncryptor.decrypt("encrypted")).willReturn("plain");
        given(appleTokenClient.revoke("plain")).willReturn(false);

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then - 빈 목록으로 파기를 호출하면 IN () 이 되어 SQL이 깨진다
        then(withdrawnMemberPurger).should(never()).purge(any());
    }
}
