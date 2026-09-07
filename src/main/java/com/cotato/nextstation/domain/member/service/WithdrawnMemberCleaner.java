package com.cotato.nextstation.domain.member.service;

import com.cotato.nextstation.domain.auth.client.KakaoOAuthClient;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 유예 기간이 끝난 탈퇴 회원의 카카오 연결을 해제하고 파기시킨다. 삭제는 WithdrawnMemberPurger가 한다.
 * <p>
 * 트랜잭션을 열지 않는다. 카카오 응답을 기다리는 동안 DB 커넥션을 잡지 않기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnMemberCleaner {

    private final MemberRepository memberRepository;
    private final MemberSocialAccountRepository memberSocialAccountRepository;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final WithdrawnMemberPurger withdrawnMemberPurger;

    // 매일 새벽 4시 30분. 같은 시간대의 EmailVerificationCleaner(4시)와 겹치지 않게 띄운다.
    // 하루 한 번이라 실제 삭제는 유예 만료 시점에서 최대 하루 늦게 일어난다.
    @Scheduled(cron = "${member.cron.withdrawn-purge:0 30 4 * * *}")
    public void purgeExpiredWithdrawals() {
        LocalDateTime threshold = LocalDateTime.now().minus(Member.WITHDRAWAL_GRACE_PERIOD);
        List<Long> targetIds = memberRepository.findIdsByStatusAndDeletedAtBefore(MemberStatus.WITHDRAWN, threshold);

        if (targetIds.isEmpty()) {
            log.info("파기 대상 탈퇴 회원 없음: threshold={}", threshold);
            return;
        }

        log.info("파기 대상 탈퇴 회원 조회: memberIds={}, threshold={}", targetIds, threshold);

        // 삭제보다 먼저 해제한다, 순서가 반대면 실패했을 때 회원번호를 잃어 카카오 연결이 영구히 남는다
        List<Long> purgeTargets = unlinkKakaoAccounts(targetIds);

        if (purgeTargets.isEmpty()) {
            log.warn("카카오 연결 해제에 모두 실패해 이번 파기를 건너뛴다: memberIds={}", targetIds);
            return;
        }

        withdrawnMemberPurger.purge(purgeTargets);
    }

    // 해제에 실패한 회원은 제외해 member_social_account 행을 남긴다, 다음 배치가 같은 회원번호로 다시 시도한다
    private List<Long> unlinkKakaoAccounts(List<Long> targetIds) {

        List<MemberSocialAccount> kakaoAccounts =
                memberSocialAccountRepository.findByMemberIdInAndProvider(targetIds, AuthProvider.KAKAO);

        if (kakaoAccounts.isEmpty()) {
            return targetIds;
        }

        // member_id에 유니크 제약이 없어 행이 여러 개일 수 있다, 하나라도 실패하면 그 회원은 제외
        Set<Long> failedMemberIds = new HashSet<>();
        for (MemberSocialAccount account : kakaoAccounts) {
            if (!kakaoOAuthClient.unlink(account.getProviderUserId())) {
                failedMemberIds.add(account.getMemberId());
            }
        }

        if (failedMemberIds.isEmpty()) {
            return targetIds;
        }

        log.warn("카카오 연결 해제 실패로 이번 파기에서 제외: memberIds={}", failedMemberIds);
        return targetIds.stream()
                .filter(id -> !failedMemberIds.contains(id))
                .toList();
    }
}
