package com.cotato.nextstation.domain.auth.service.command;

import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.service.AuthTokenIssuer;
import com.cotato.nextstation.domain.auth.service.IssuedTokens;
import com.cotato.nextstation.domain.auth.service.result.ProfileSetupResult;
import com.cotato.nextstation.domain.auth.util.SignupTokenClaims;
import com.cotato.nextstation.domain.member.entity.Gender;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.exception.NicknameErrorCode;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.util.NicknameValidator;
import com.cotato.nextstation.domain.member.util.ProfileImageUrlValidator;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileSetupCommandService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MemberRepository memberRepository;
    private final NicknameValidator nicknameValidator;
    private final ProfileImageUrlValidator profileImageUrlValidator;
    private final JwtProvider jwtProvider;
    private final AuthTokenIssuer authTokenIssuer;

    @Transactional
    public ProfileSetupResult setupProfile(String authorizationHeader, String nickname, String profileImageUrl,
                                              Gender gender, LocalDate birthDate) {
        Long memberId = resolveMemberId(authorizationHeader);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.MEMBER_NOT_FOUND));

        validateProfileNotAlreadyCompleted(member);
        nicknameValidator.validate(nickname);
        if (profileImageUrl != null && !profileImageUrl.isBlank()) {
            profileImageUrlValidator.validate(profileImageUrl, memberId);
        }

        claimProfileCompletion(memberId);

        member.completeProfile(nickname, profileImageUrl, gender, birthDate);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 위 existsByNickname 조회 이후 동시에 같은 닉네임으로 들어온 요청이 먼저 커밋된 경우 (레이스 컨디션)
            log.warn("닉네임 중복 저장 시도(레이스 컨디션): nickname={}", nickname);
            throw new CustomException(NicknameErrorCode.DUPLICATE_NICKNAME);
        }

        // 프로필 설정이 회원가입의 마지막 단계이므로, 여기서 바로 로그인 세션을 열어 재로그인을 요구하지 않는다.
        // signupToken 자체가 이메일 인증/카카오 인증을 통과했다는 증명이라 추가 인증 없이 발급해도 된다.
        IssuedTokens tokens = authTokenIssuer.issue(memberId);

        log.info("프로필 설정 완료 및 로그인 세션 발급: memberId={}", memberId);
        return new ProfileSetupResult(member.getId(), member.getNickname(), member.getStatus(),
                tokens.accessToken(), tokens.refreshToken());
    }

    // signupToken 파싱/검증 (purpose=SIGNUP인 JWT만 허용) 후 memberId(subject) 추출
    private Long resolveMemberId(String authorizationHeader) {
        String token = extractToken(authorizationHeader);

        Claims claims;
        try {
            claims = jwtProvider.parseClaims(token);
        } catch (ExpiredJwtException e) {
            throw new CustomException(AuthErrorCode.SIGNUP_TOKEN_EXPIRED);
        } catch (JwtException e) {
            throw new CustomException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
        }

        if (!SignupTokenClaims.SIGNUP_PURPOSE.equals(claims.get(SignupTokenClaims.PURPOSE_KEY, String.class))) {
            throw new CustomException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
        }

        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new CustomException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
        }
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new CustomException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
        }
        return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    }

    /**
     * PENDING -> ACTIVE 전환을 조건부 갱신으로 선점한다. 이 전환에 성공한 요청만 프로필을 저장하고 토큰을 발급받는다.
     * 갱신된 행이 없으면 조회 이후 다른 요청이 먼저 전환을 끝낸 것이므로 재요청과 동일하게 거부한다.
     * 트랜잭션이 롤백되면 선점도 함께 되돌아가므로, 이후 검증 실패로 실패한 요청이 상태를 붙잡고 있지는 않는다.
     */
    private void claimProfileCompletion(Long memberId) {
        if (memberRepository.activateIfPending(memberId) == 0) {
            log.warn("동시 요청으로 이미 프로필 설정이 완료되어 상태 선점 실패: memberId={}", memberId);
            throw new CustomException(AuthErrorCode.PROFILE_ALREADY_COMPLETED);
        }
    }

    // 이미 프로필 설정을 마친 회원의 재요청을 쓰기 시도 전에 걸러내는 빠른 경로.
    // 동시 요청까지 막지는 못하므로 실제 중복 방지는 claimProfileCompletion의 조건부 갱신이 담당한다.
    private void validateProfileNotAlreadyCompleted(Member member) {
        if (member.getStatus() != MemberStatus.PENDING) {
            throw new CustomException(AuthErrorCode.PROFILE_ALREADY_COMPLETED);
        }
    }
}