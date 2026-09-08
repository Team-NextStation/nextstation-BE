package com.cotato.nextstation.domain.member.service;

import com.cotato.nextstation.domain.auth.client.KakaoOAuthClient;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private WithdrawnMemberPurger withdrawnMemberPurger;

    @Test
    @DisplayName("대상이 없으면 조회만 하고 끝낸다")
    void purge_noTargets() {
        // given
        givenTargets(List.of());

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        then(withdrawnMemberPurger).should(never()).purge(any());
        then(kakaoOAuthClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소셜 연동이 없는 회원은 연결 해제 없이 파기한다")
    void purge_localMembers() {
        // given
        givenTargets(List.of(1L, 2L));
        givenKakaoAccounts(List.of());

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then
        then(kakaoOAuthClient).shouldHaveNoInteractions();
        then(withdrawnMemberPurger).should().purge(List.of(1L, 2L));
    }

    @Test
    @DisplayName("카카오 연결을 해제한 뒤 파기한다")
    void purge_unlinksBeforeDelete() {
        // given
        givenTargets(List.of(1L, 2L));
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
    @DisplayName("연결 해제에 실패한 회원만 파기 대상에서 빠진다")
    void purge_excludesUnlinkFailure() {
        // given
        givenTargets(List.of(1L, 2L, 3L));
        givenKakaoAccounts(List.of(kakaoAccount(1L, "kakao-1"), kakaoAccount(2L, "kakao-2")));
        given(kakaoOAuthClient.unlink("kakao-1")).willReturn(true);
        given(kakaoOAuthClient.unlink("kakao-2")).willReturn(false);

        // then - 실패한 2번만 빠지고, 소셜 연동이 없는 3번은 그대로 파기된다
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        then(withdrawnMemberPurger).should().purge(List.of(1L, 3L));
    }

    @Test
    @DisplayName("연결 해제에 모두 실패하면 파기하지 않는다")
    void purge_skipsWhenAllUnlinkFail() {
        // given
        givenTargets(List.of(1L));
        givenKakaoAccounts(List.of(kakaoAccount(1L, "kakao-1")));
        given(kakaoOAuthClient.unlink("kakao-1")).willReturn(false);

        // when
        withdrawnMemberCleaner.purgeExpiredWithdrawals();

        // then - 빈 목록으로 파기를 호출하면 IN () 이 되어 SQL이 깨진다
        then(withdrawnMemberPurger).should(never()).purge(any());
    }

    private void givenTargets(List<Long> memberIds) {
        given(memberRepository.findIdsByStatusAndDeletedAtBefore(eq(MemberStatus.WITHDRAWN), any()))
                .willReturn(memberIds);
    }

    private void givenKakaoAccounts(List<MemberSocialAccount> accounts) {
        given(memberSocialAccountRepository.findByMemberIdInAndProvider(any(), eq(AuthProvider.KAKAO)))
                .willReturn(accounts);
    }

    private MemberSocialAccount kakaoAccount(Long memberId, String providerUserId) {
        return MemberSocialAccount.builder()
                .memberId(memberId)
                .provider(AuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .build();
    }
}
