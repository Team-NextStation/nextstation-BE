package com.cotato.nextstation.domain.member.service;

import com.cotato.nextstation.domain.auth.client.AppleTokenClient;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.entity.SocialOauthCredential;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import com.cotato.nextstation.domain.member.repository.SocialOauthCredentialRepository;
import com.cotato.nextstation.global.security.OAuthRefreshTokenEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 유예 기간이 끝난 탈퇴 회원의 Apple 연동을 해제하고 파기시킨다. 삭제는 WithdrawnMemberPurger가 한다.
 * 카카오는 별도 이슈(#22)에서 다룬다 - 아직 refresh_token을 저장하지 않는다.
 * <p>
 * 트랜잭션을 열지 않는다. Apple 응답을 기다리는 동안 DB 커넥션을 잡지 않기 위해서다.
 * <p>
 * revoke를 탈퇴 요청 즉시가 아니라 이 배치(유예 종료) 시점에 하는 이유: 유예 기간 중에는 재로그인으로
 * 계정이 복구될 수 있는데, 탈퇴 즉시 revoke해버리면 복구된 계정의 Apple 연동 상태가 DB(저장된 refresh_token)와
 * 어긋난다. 실제로 파기되어 되돌릴 수 없는 시점에만 revoke하면 이 문제가 생기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnMemberCleaner {

    private final MemberRepository memberRepository;
    private final MemberSocialAccountRepository memberSocialAccountRepository;
    private final SocialOauthCredentialRepository socialOauthCredentialRepository;
    private final OAuthRefreshTokenEncryptor oAuthRefreshTokenEncryptor;
    private final AppleTokenClient appleTokenClient;
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

        // 삭제보다 먼저 해제한다, 순서가 반대면 실패했을 때 회원번호(providerUserId)를 잃어 Apple 연동이 영구히 남는다
        List<Long> purgeTargets = revokeAppleTokens(targetIds);

        if (purgeTargets.isEmpty()) {
            log.warn("Apple 연동 해제에 모두 실패해 이번 파기를 건너뛴다: memberIds={}", targetIds);
            return;
        }

        withdrawnMemberPurger.purge(purgeTargets);
    }

    // 해제에 실패한 회원은 제외해 social_oauth_credential 행을 남긴다, 다음 배치가 같은 refresh_token으로 다시 시도한다
    private List<Long> revokeAppleTokens(List<Long> targetIds) {

        List<MemberSocialAccount> appleAccounts =
                memberSocialAccountRepository.findByMemberIdInAndProvider(targetIds, AuthProvider.APPLE);

        if (appleAccounts.isEmpty()) {
            return targetIds;
        }

        Map<Long, Long> memberIdByAccountId = appleAccounts.stream()
                .collect(Collectors.toMap(MemberSocialAccount::getId, MemberSocialAccount::getMemberId));

        List<SocialOauthCredential> credentials = socialOauthCredentialRepository
                .findByMemberSocialAccountIdIn(memberIdByAccountId.keySet());

        // 가입 시 저장이 실패했거나 Apple Key 발급 전에 가입한 회원 - revoke할 게 없으니 그대로 파기 대상에 남긴다
        Set<Long> failedMemberIds = new HashSet<>();
        for (SocialOauthCredential credential : credentials) {
            Long memberId = memberIdByAccountId.get(credential.getMemberSocialAccountId());
            if (!revokeOne(credential)) {
                failedMemberIds.add(memberId);
            }
        }

        if (failedMemberIds.isEmpty()) {
            return targetIds;
        }

        log.warn("Apple 연동 해제 실패로 이번 파기에서 제외: memberIds={}", failedMemberIds);
        return targetIds.stream()
                .filter(id -> !failedMemberIds.contains(id))
                .toList();
    }

    private boolean revokeOne(SocialOauthCredential credential) {
        try {
            String refreshToken = oAuthRefreshTokenEncryptor.decrypt(credential.getRefreshToken());
            return appleTokenClient.revoke(refreshToken);
        } catch (Exception e) {
            log.warn("Apple refresh_token 복호화/revoke 중 오류: memberSocialAccountId={}", credential.getMemberSocialAccountId(), e);
            return false;
        }
    }
}
