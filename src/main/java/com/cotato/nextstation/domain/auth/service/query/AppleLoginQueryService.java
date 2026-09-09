package com.cotato.nextstation.domain.auth.service.query;

import com.cotato.nextstation.domain.auth.client.AppleOAuthClient;
import com.cotato.nextstation.domain.auth.client.AppleTokenClient;
import com.cotato.nextstation.domain.auth.client.dto.AppleIdentityToken;
import com.cotato.nextstation.domain.auth.client.dto.AppleTokenResponse;
import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.repository.PendingAppleCredentialRepository;
import com.cotato.nextstation.domain.auth.service.AuthTokenIssuer;
import com.cotato.nextstation.domain.auth.service.IssuedTokens;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResult;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResultType;
import com.cotato.nextstation.domain.auth.util.AppleSignupTokenClaims;
import com.cotato.nextstation.domain.auth.util.SignupTokenClaims;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import com.cotato.nextstation.domain.member.service.command.MemberCommandService;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.jwt.JwtProvider;
import com.cotato.nextstation.global.security.OAuthRefreshTokenEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

// login()이 외부 API 호출(JWKS 조회, NEW_MEMBER일 때 Apple 토큰교환)을 포함할 수 있는데, 트랜잭션으로 감싸면
// 그 호출이 끝날 때까지 DB 커넥션을 붙잡고 있게 되므로 붙이지 않는다.
// 카카오와 달리 로그인 판별 자체엔 토큰교환/사용자정보조회가 없다 - 클라이언트(iOS 네이티브)가 이미 들고 있는
// identity token을 검증만 한다. authorizationCode 교환은 신규 회원의 revoke 준비를 위한 부가 작업이다.
@Slf4j
@Service
@RequiredArgsConstructor
public class AppleLoginQueryService {

    private static final Duration APPLE_SIGNUP_TOKEN_EXPIRATION = Duration.ofMinutes(10);
    private static final Duration SIGNUP_TOKEN_EXPIRATION = Duration.ofMinutes(30);

    private final AppleOAuthClient appleOAuthClient;
    private final AppleTokenClient appleTokenClient;
    private final OAuthRefreshTokenEncryptor oAuthRefreshTokenEncryptor;
    private final PendingAppleCredentialRepository pendingAppleCredentialRepository;
    private final MemberRepository memberRepository;
    private final MemberSocialAccountRepository memberSocialAccountRepository;
    private final JwtProvider jwtProvider;
    private final AuthTokenIssuer authTokenIssuer;
    private final MemberCommandService memberCommandService;

    // identity token 검증 후 신규/PENDING/기존 회원 3분기 판별, Member 생성은 여기서 하지 않는다(AppleSignupCommandService 담당)
    public AppleLoginResult login(String identityToken, String nonce, String authorizationCode) {

        AppleIdentityToken appleIdentityToken = appleOAuthClient.verify(identityToken, nonce);
        String providerUserId = appleIdentityToken.providerUserId();

        Optional<MemberSocialAccount> socialAccount =
                memberSocialAccountRepository.findByProviderAndProviderUserId(AuthProvider.APPLE, providerUserId);

        if (socialAccount.isEmpty()) {
            log.info("신규 Apple 회원 로그인 시도: providerUserId={}", providerUserId);
            cachePendingCredentialIfPresent(providerUserId, authorizationCode);
            return issueAppleSignupToken(providerUserId, appleIdentityToken);
        }

        Long memberId = socialAccount.get().getMemberId();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> {
                    // memberId가 scalar FK라 DB가 정합성을 보장 안 함 -> 정상 흐름에서는 절대 발생하면 안 되는 케이스
                    log.error("member_social_account는 있는데 member가 없음(데이터 정합성 오류): memberId={}", memberId);
                    return new CustomException(AuthErrorCode.MEMBER_NOT_FOUND);
                });

        // Apple 인증을 통과한 것 자체가 본인 확인이므로, 유예 기간이 남아있으면 그대로 복구한다.
        // 이 클래스는 트랜잭션이 없어 dirty checking이 동작하지 않으므로 쓰기는 커맨드 서비스에 위임한다.
        MemberStatus status = member.getStatus();
        boolean restored = false;
        if (member.isRestorable()) {
            MemberStatus statusBeforeRestore = status;
            status = memberCommandService.restore(member.getId());
            // 동시 복구 요청 경쟁에서 밀리면 restore()가 복구 없이 기존 status를 그대로 반환하므로,
            // 실제로 상태가 바뀐 경우에만 restored=true로 응답한다.
            restored = status != statusBeforeRestore;
            log.info("탈퇴 유예 기간 내 Apple 재로그인으로 계정 복구: memberId={}, restoredStatus={}, restored={}", member.getId(), status, restored);
        }

        if (status == MemberStatus.PENDING) {
            log.info("PENDING 상태 Apple 회원 재로그인: memberId={}", member.getId());
            return reissueSignupTokenForPendingMember(member, restored);
        }
        if (status != MemberStatus.ACTIVE) {
            log.warn("ACTIVE/PENDING이 아닌 Apple 회원의 로그인 시도: memberId={}, status={}", member.getId(), status);
            throw new CustomException(AuthErrorCode.APPLE_MEMBER_NOT_ACTIVE);
        }

        log.info("Apple 로그인 성공: memberId={}, restored={}", member.getId(), restored);
        IssuedTokens tokens = authTokenIssuer.issue(member.getId());

        return new AppleLoginResult(AppleLoginResultType.LOGIN_SUCCESS, member.getId(), tokens.accessToken(), tokens.refreshToken(),
                null, null, restored, member.getRole());
    }

    // authorizationCode는 1회용에 수명도 짧아서, 받은 즉시(여기, 로그인 판별 시점) 교환해 pending에 캐싱해둔다.
    // 나중에 /apple/signup에서 이 값을 그대로 가져다 쓰면, 그 시점엔 Apple API 호출 없이 로컬 저장만으로 끝나서
    // "약관 동의 화면을 오래 보다 code가 만료되는" 문제와 "교환 성공 후 로컬 저장만 실패하는" 문제를 둘 다 피한다.
    // 실패해도(예: Apple Key 발급 전) 로그인 판별 자체는 막지 않는다 - 부가 기능 손실일 뿐이다.
    private void cachePendingCredentialIfPresent(String providerUserId, String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            return;
        }
        try {
            AppleTokenResponse tokenResponse = appleTokenClient.exchangeAuthorizationCode(authorizationCode);
            String encryptedRefreshToken = oAuthRefreshTokenEncryptor.encrypt(tokenResponse.refreshToken());
            pendingAppleCredentialRepository.save(providerUserId, encryptedRefreshToken);
        } catch (Exception e) {
            log.warn("Apple authorizationCode 교환/캐싱 실패(로그인 판별은 정상 처리) - 가입해도 이 회원의 " +
                    "Apple 연동은 탈퇴 시 자동 해제되지 않는다: providerUserId={}", providerUserId, e);
        }
    }

    private AppleLoginResult issueAppleSignupToken(String providerUserId, AppleIdentityToken appleIdentityToken) {

        // Map.of()는 value가 null이면 NPE를 던지므로, Hide My Email 등으로 null일 수 있는 값은 빈 문자열로 치환
        Map<String, Object> claims = Map.of(
                AppleSignupTokenClaims.PURPOSE_KEY, AppleSignupTokenClaims.APPLE_SIGNUP_PURPOSE,
                AppleSignupTokenClaims.EMAIL_KEY, orEmpty(appleIdentityToken.email())
        );

        String appleSignupToken = jwtProvider.generateToken(providerUserId, claims, APPLE_SIGNUP_TOKEN_EXPIRATION);

        return new AppleLoginResult(AppleLoginResultType.NEW_MEMBER, null, null, null,
                null, appleSignupToken, false, null);
    }

    private AppleLoginResult reissueSignupTokenForPendingMember(Member member, boolean restored) {

        String signupToken = jwtProvider.generateToken(
                member.getId().toString(),
                Map.of(SignupTokenClaims.PURPOSE_KEY, SignupTokenClaims.SIGNUP_PURPOSE),
                SIGNUP_TOKEN_EXPIRATION
        );
        return new AppleLoginResult(AppleLoginResultType.PENDING_PROFILE, member.getId(), null, null,
                signupToken, null, restored, null);
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
