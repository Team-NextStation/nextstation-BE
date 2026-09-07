package com.cotato.nextstation.domain.auth.service.command;

import com.cotato.nextstation.domain.auth.client.AppleTokenClient;
import com.cotato.nextstation.domain.auth.client.dto.AppleTokenResponse;
import com.cotato.nextstation.domain.auth.dto.response.SignupResponse;
import com.cotato.nextstation.domain.auth.entity.MemberTermsAgreement;
import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.repository.MemberTermsAgreementRepository;
import com.cotato.nextstation.domain.auth.util.AppleSignupTokenClaims;
import com.cotato.nextstation.domain.auth.util.SignupTokenClaims;
import com.cotato.nextstation.domain.auth.util.TermsAgreementValidator;
import com.cotato.nextstation.domain.member.entity.AuthProvider;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberSocialAccount;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.entity.SocialOauthCredential;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.repository.MemberSocialAccountRepository;
import com.cotato.nextstation.domain.member.repository.SocialOauthCredentialRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.jwt.JwtProvider;
import com.cotato.nextstation.global.security.OAuthRefreshTokenEncryptor;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppleSignupCommandService {

    private static final Duration SIGNUP_TOKEN_EXPIRATION = Duration.ofMinutes(30);

    private final MemberRepository memberRepository;
    private final MemberSocialAccountRepository memberSocialAccountRepository;
    private final MemberTermsAgreementRepository memberTermsAgreementRepository;
    private final SocialOauthCredentialRepository socialOauthCredentialRepository;
    private final JwtProvider jwtProvider;
    private final TermsAgreementValidator termsAgreementValidator;
    private final AppleTokenClient appleTokenClient;
    private final OAuthRefreshTokenEncryptor oAuthRefreshTokenEncryptor;

    @Transactional
    public SignupResponse signup(String appleSignupToken, List<Long> agreedTermsIds, String ipAddress, String authorizationCode) {

        AppleSignupClaims appleClaims = resolveAppleClaims(appleSignupToken);
        log.info("Apple 회원가입 요청: providerUserId={}", appleClaims.providerUserId());

        // 약관 동의 화면이 뜬 사이 중복 요청으로 이미 연동됐으면 재가입이 아니라 signupToken만 재발급
        Optional<MemberSocialAccount> existingSocialAccount = memberSocialAccountRepository
                .findByProviderAndProviderUserId(AuthProvider.APPLE, appleClaims.providerUserId());
        if (existingSocialAccount.isPresent()) {
            return reissueForExistingMember(existingSocialAccount.get().getMemberId());
        }

        termsAgreementValidator.validate(agreedTermsIds);

        // 정상 발급 경로(issueAppleSignupToken)는 항상 빈 문자열로 채워 email이 null일 수 없지만,
        // 그 불변식이 깨지는 상황(발급 경로 변경 등)에도 NPE 대신 안전하게 null로 처리한다.
        String email = (appleClaims.email() == null || appleClaims.email().isBlank()) ? null : appleClaims.email();

        // Apple 인증 이메일이 기존 로컬(이메일/비밀번호) 계정과 겹치는 경우, 계정 연동은 아직 미지원이라 명확한 에러로 막는다.
        if (email != null && memberRepository.existsByEmail(email)) {
            log.warn("Apple 인증 이메일이 기존 계정과 중복: providerUserId={}", appleClaims.providerUserId());
            throw new CustomException(AuthErrorCode.DUPLICATE_EMAIL);
        }

        Member member;
        MemberSocialAccount socialAccount;
        try {
            member = memberRepository.save(Member.builder().email(email).build());
            socialAccount = memberSocialAccountRepository.save(
                    MemberSocialAccount.builder()
                            .memberId(member.getId())
                            .provider(AuthProvider.APPLE)
                            .providerUserId(appleClaims.providerUserId())
                            .email(email)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            // 위 조회 이후 동시에 같은 providerUserId로 들어온 요청이 먼저 저장된 경우 (레이스 컨디션)
            // -> Member/MemberSocialAccount 둘 다 같은 트랜잭션이라 여기서 던지면 함께 롤백됨
            log.warn("Apple 회원 중복 저장 시도(레이스 컨디션): providerUserId={}", appleClaims.providerUserId());
            throw new CustomException(AuthErrorCode.APPLE_ACCOUNT_ALREADY_REGISTERED);
        }

        List<MemberTermsAgreement> agreements = agreedTermsIds.stream()
                .distinct()
                .map(termsConsentId -> MemberTermsAgreement.builder()
                        .memberId(member.getId())
                        .termsConsentsId(termsConsentId)
                        .agreed(true)
                        .ipAddress(ipAddress)
                        .build())
                .toList();
        memberTermsAgreementRepository.saveAll(agreements);

        // authorizationCode 교환은 Apple 서버에 되돌릴 수 없는 부수효과(1회용 code 소비)를 일으킨다.
        // 이후에도 실패할 수 있는 로컬 저장(약관 동의 등)을 다 끝낸 뒤 트랜잭션의 맨 마지막에 호출해야,
        // 뒤이은 로컬 실패로 전체가 롤백되면서 이미 소비된 code만 날리고 credential은 못 남기는 상황을 피할 수 있다.
        saveOauthCredential(socialAccount.getId(), authorizationCode);

        String signupToken = issueSignupToken(member.getId());
        log.info("Apple 회원가입 완료: memberId={}", member.getId());
        return new SignupResponse(member.getId(), signupToken);
    }

    private SignupResponse reissueForExistingMember(Long memberId) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.MEMBER_NOT_FOUND));
        if (member.getStatus() != MemberStatus.PENDING) {
            log.warn("이미 프로필 설정까지 완료된 Apple 회원의 재가입 시도: memberId={}", member.getId());
            throw new CustomException(AuthErrorCode.APPLE_ACCOUNT_ALREADY_REGISTERED);
        }

        String signupToken = issueSignupToken(member.getId());

        log.info("Apple 회원 signupToken 재발급: memberId={}", member.getId());
        return new SignupResponse(member.getId(), signupToken);
    }

    private String issueSignupToken(Long memberId) {
        return jwtProvider.generateToken(
                memberId.toString(),
                Map.of(SignupTokenClaims.PURPOSE_KEY, SignupTokenClaims.SIGNUP_PURPOSE),
                SIGNUP_TOKEN_EXPIRATION
        );
    }

    // authorizationCode를 refresh_token으로 교환해 암호화 저장한다 - 탈퇴 시 Apple 쪽 연동을 revoke하기 위한 준비.
    // 실패해도(예: Apple Key 발급 전, authorizationCode 만료 등) 가입 자체는 그대로 진행한다 - 이건
    // "나중에 탈퇴할 때 자동으로 못 끊는다"는 부가 기능 손실일 뿐, 핵심 가입 흐름을 막을 이유가 아니다.
    private void saveOauthCredential(Long memberSocialAccountId, String authorizationCode) {
        try {
            AppleTokenResponse tokenResponse = appleTokenClient.exchangeAuthorizationCode(authorizationCode);
            String encryptedRefreshToken = oAuthRefreshTokenEncryptor.encrypt(tokenResponse.refreshToken());

            socialOauthCredentialRepository.save(
                    SocialOauthCredential.builder()
                            .memberSocialAccountId(memberSocialAccountId)
                            .provider(AuthProvider.APPLE)
                            .refreshToken(encryptedRefreshToken)
                            .build()
            );
        } catch (Exception e) {
            log.warn("Apple refresh_token 저장 실패(가입은 정상 처리) - 이 회원은 탈퇴해도 Apple 쪽 연동이 자동 해제되지 않는다: memberSocialAccountId={}",
                    memberSocialAccountId, e);
        }
    }

    // subject는 memberId가 아니라 providerUserId(Apple 회원번호)
    private AppleSignupClaims resolveAppleClaims(String appleSignupToken) {

        Claims claims;

        try {
            claims = jwtProvider.parseClaims(appleSignupToken);
        } catch (ExpiredJwtException e) {
            throw new CustomException(AuthErrorCode.APPLE_SIGNUP_TOKEN_EXPIRED);
        } catch (JwtException e) {
            throw new CustomException(AuthErrorCode.INVALID_APPLE_SIGNUP_TOKEN);
        }

        if (!AppleSignupTokenClaims.APPLE_SIGNUP_PURPOSE.equals(claims.get(AppleSignupTokenClaims.PURPOSE_KEY, String.class))) {
            throw new CustomException(AuthErrorCode.INVALID_APPLE_SIGNUP_TOKEN);
        }

        String providerUserId = claims.getSubject();
        String email = claims.get(AppleSignupTokenClaims.EMAIL_KEY, String.class);
        return new AppleSignupClaims(providerUserId, email);
    }

    private record AppleSignupClaims(String providerUserId, String email) {
    }
}
