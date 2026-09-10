package com.cotato.nextstation.domain.member.service;

import com.cotato.nextstation.domain.auth.client.AppleTokenClient;
import com.cotato.nextstation.domain.auth.client.KakaoOAuthClient;
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
 * 유예 기간이 끝난 탈퇴 회원의 소셜 연동(Apple/카카오)을 해제하고 파기시킨다. 삭제는 WithdrawnMemberPurger가 한다.
 * <p>
 * 트랜잭션을 열지 않는다. Apple/카카오 응답을 기다리는 동안 DB 커넥션을 잡지 않기 위해서다.
 * <p>
 * revoke/unlink를 탈퇴 요청 즉시가 아니라 이 배치(유예 종료) 시점에 하는 이유: 유예 기간 중에는 재로그인으로
 * 계정이 복구될 수 있는데, 탈퇴 즉시 끊어버리면 복구된 계정의 소셜 연동 상태가 DB와 어긋난다.
 * 실제로 파기되어 되돌릴 수 없는 시점에만 해제하면 이 문제가 생기지 않는다.
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

        // 삭제보다 먼저 해제한다, 순서가 반대면 실패했을 때 회원번호(providerUserId)를 잃어 연동이 영구히 남는다.
        // Apple/카카오 각각 실패한 회원 집합을 구해서 합집합만큼 이번 파기 대상에서 제외한다.
        Set<Long> appleFailedMemberIds = revokeAppleTokens(targetIds);
        Set<Long> kakaoFailedMemberIds = unlinkKakaoAccounts(targetIds);

        List<Long> purgeTargets = targetIds.stream()
                .filter(id -> !appleFailedMemberIds.contains(id) && !kakaoFailedMemberIds.contains(id))
                .toList();

        if (purgeTargets.isEmpty()) {
            log.warn("소셜 연동 해제에 모두 실패해 이번 파기를 건너뛴다: memberIds={}", targetIds);
            return;
        }

        withdrawnMemberPurger.purge(purgeTargets);
    }

    // 해제에 실패한 회원은 제외해 social_oauth_credential 행을 남긴다, 다음 배치가 같은 refresh_token으로 다시 시도한다
    private Set<Long> revokeAppleTokens(List<Long> targetIds) {

        List<MemberSocialAccount> appleAccounts =
                memberSocialAccountRepository.findByMemberIdInAndProvider(targetIds, AuthProvider.APPLE);

        if (appleAccounts.isEmpty()) {
            return Set.of();
        }

        Map<Long, Long> memberIdByAccountId = appleAccounts.stream()
                .collect(Collectors.toMap(MemberSocialAccount::getId, MemberSocialAccount::getMemberId));

        List<SocialOauthCredential> credentials = socialOauthCredentialRepository
                .findByMemberSocialAccountIdIn(memberIdByAccountId.keySet());

        // 가입 시 저장이 실패했거나 Apple Key 발급 전에 가입한 회원 - revoke할 게 없으니 실패로 치지 않는다.
        Set<Long> attemptedMemberIds = new HashSet<>();
        Set<Long> failedMemberIds = new HashSet<>();
        for (SocialOauthCredential credential : credentials) {
            Long memberId = memberIdByAccountId.get(credential.getMemberSocialAccountId());
            attemptedMemberIds.add(memberId);
            if (!revokeOne(credential)) {
                failedMemberIds.add(memberId);
            }
        }

        logFailures("Apple", failedMemberIds, attemptedMemberIds.size());
        return failedMemberIds;
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

    // 해제에 실패한 회원은 제외해 member_social_account 행을 남긴다, 다음 배치가 같은 회원번호로 다시 시도한다
    private Set<Long> unlinkKakaoAccounts(List<Long> targetIds) {

        List<MemberSocialAccount> kakaoAccounts =
                memberSocialAccountRepository.findByMemberIdInAndProvider(targetIds, AuthProvider.KAKAO);

        if (kakaoAccounts.isEmpty()) {
            return Set.of();
        }

        // member_id에 유니크 제약이 없어 행이 여러 개일 수 있다, 하나라도 실패하면 그 회원은 제외
        Set<Long> attemptedMemberIds = new HashSet<>();
        Set<Long> failedMemberIds = new HashSet<>();
        for (MemberSocialAccount account : kakaoAccounts) {
            attemptedMemberIds.add(account.getMemberId());
            if (!kakaoOAuthClient.unlink(account.getProviderUserId())) {
                failedMemberIds.add(account.getMemberId());
            }
        }

        logFailures("카카오", failedMemberIds, attemptedMemberIds.size());
        return failedMemberIds;
    }

    // 일부만 실패하면 그 회원들 refresh_token/연동 정보가 아직 남아있어 다음 배치가 알아서 재시도한다(WARN으로 충분).
    // 시도한 회원 전원이 실패하면 개별 계정 문제가 아니라 어드민 키 만료·인증서 문제 같은 설정/연동 자체의
    // 장애일 가능성이 높고, 그 상태로는 파기가 계속 밀리므로 놓치지 않도록 ERROR로 올린다.
    private void logFailures(String provider, Set<Long> failedMemberIds, int attemptedCount) {
        if (failedMemberIds.isEmpty()) {
            return;
        }
        if (failedMemberIds.size() == attemptedCount) {
            log.error("{} 연동 해제가 전원 실패했다 - 설정/연동 자체의 문제일 수 있다: memberIds={}", provider, failedMemberIds);
        } else {
            log.warn("{} 연동 해제 실패로 이번 파기에서 제외: memberIds={}", provider, failedMemberIds);
        }
    }
}
