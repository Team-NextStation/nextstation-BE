package com.cotato.nextstation.domain.member.service;

import com.cotato.nextstation.domain.auth.client.AppleTokenClient;
import com.cotato.nextstation.domain.auth.repository.RefreshSessionRepository;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.entity.SocialOauthCredential;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import com.cotato.nextstation.domain.member.repository.SocialOauthCredentialRepository;
import com.cotato.nextstation.domain.member.service.command.MemberCommandService;
import com.cotato.nextstation.global.security.OAuthRefreshTokenEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 탈퇴의 DB 처리와, 그 뒤에 따라오는 부가 정리 작업(Redis 세션 삭제, Apple 쪽 연동 revoke)을 순서대로 묶는다.
 * <p>
 * 트랜잭션을 열지 않는다.
 * 이 정리 작업들이 트랜잭션 안에서 일어나면 DB가 롤백돼도 Redis/Apple 쪽은 되돌아오지 않아
 * "로그아웃은/revoke는 됐는데 탈퇴는 안 된" 상태가 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberWithdrawService {

    private final MemberCommandService memberCommandService;
    private final RefreshSessionRepository refreshSessionRepository;
    private final MemberSocialAccountRepository memberSocialAccountRepository;
    private final SocialOauthCredentialRepository socialOauthCredentialRepository;
    private final OAuthRefreshTokenEncryptor oAuthRefreshTokenEncryptor;
    private final AppleTokenClient appleTokenClient;

    public void withdraw(Long memberId) {
        memberCommandService.withdraw(memberId);

        try {
            int deletedSessions = refreshSessionRepository.deleteAllOf(memberId);
            log.info("탈퇴 회원 세션 정리 완료: memberId={}, deletedSessions={}", memberId, deletedSessions);
        } catch (Exception e) {
            log.error("탈퇴 회원 세션 정리 실패 - 탈퇴 자체는 완료됨: memberId={}", memberId, e);
        }

        revokeAppleTokenIfPresent(memberId);
    }

    // 카카오는 별도 이슈(#19 참고)에서 다룬다 - 아직 refresh_token을 저장하지 않는다.
    // 로컬/카카오 회원이거나, Apple Key 발급 전에 가입해 refresh_token이 저장되지 않은 회원이면 조용히 건너뛴다.
    private void revokeAppleTokenIfPresent(Long memberId) {
        Optional<MemberSocialAccount> socialAccount = memberSocialAccountRepository.findFirstByMemberIdOrderByIdAsc(memberId);
        if (socialAccount.isEmpty() || socialAccount.get().getProvider() != AuthProvider.APPLE) {
            return;
        }

        Optional<SocialOauthCredential> credential =
                socialOauthCredentialRepository.findByMemberSocialAccountId(socialAccount.get().getId());
        if (credential.isEmpty()) {
            log.info("Apple refresh_token이 저장되어 있지 않아 revoke를 건너뜀: memberId={}", memberId);
            return;
        }

        try {
            String refreshToken = oAuthRefreshTokenEncryptor.decrypt(credential.get().getRefreshToken());
            appleTokenClient.revoke(refreshToken);
            log.info("Apple refresh_token revoke 완료: memberId={}", memberId);
        } catch (Exception e) {
            // 탈퇴 자체는 이미 완료된 상태 - 로그만 남기고 무시한다.
            // Apple 쪽에 연동이 남아있게 되지만, 회원 개인정보는 이미 파기 대상(soft delete)으로 전환됐다.
            log.warn("Apple refresh_token revoke 실패(무시): memberId={}", memberId, e);
        }
    }
}