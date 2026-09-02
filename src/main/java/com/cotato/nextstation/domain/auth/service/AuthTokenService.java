package com.cotato.nextstation.domain.auth.service;

import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.repository.RefreshSessionRepository;
import com.cotato.nextstation.domain.auth.service.result.LoginResult;
import com.cotato.nextstation.domain.auth.service.result.ReissueResult;
import com.cotato.nextstation.domain.auth.util.EmailMasker;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.member.service.command.MemberCommandService;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.jwt.AuthTokenClaims;
import com.cotato.nextstation.global.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 로그인 세션(access/refresh token)의 발급·회전·폐기를 담당한다.
 * 부수효과: DB는 조회만 하지만, Redis의 refresh 세션 상태는 변경한다.
 *  - login()   세션 생성
 *  - reissue() 세션의 jti 회전
 *  - logout()  세션 삭제
 * 상태 변경이 DB 트랜잭션 밖에서 일어나므로 Command/Query 접미사 대신 여기에 명시한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthTokenService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthTokenIssuer authTokenIssuer;
    private final RefreshSessionRepository refreshSessionRepository;
    private final MemberCommandService memberCommandService;

    // 로그인 자체는 트랜잭션을 열지 않는다. 복구 쓰기를 MemberCommandService의 별도 트랜잭션에 맡겨,
    // 프로필 미설정 상태로 탈퇴한 회원이 복구되자마자 PENDING으로 걸려 실패하더라도 복구 자체는 롤백되지 않게 한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LoginResult login(String email, String password) {

        // 이메일 존재 여부와 비밀번호 불일치를 구분하지 않고 동일한 에러로 응답한다 (계정 존재 여부 노출 방지)
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 이메일로 로그인 시도: email={}", EmailMasker.mask(email));
                    return new CustomException(AuthErrorCode.INVALID_CREDENTIALS);
                });

        // 상태 확인보다 먼저 비밀번호를 검증한다. 탈퇴 회원을 복구하려면 본인이라는 게 먼저 증명돼야 한다.
        if (!passwordEncoder.matches(password, member.getPassword())) {
            log.warn("비밀번호 불일치로 로그인 실패: memberId={}", member.getId());
            throw new CustomException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        // 복구는 별도 트랜잭션(MemberCommandService)에서 즉시 커밋되므로, 이후 PENDING으로 걸려 로그인이 실패해도 되돌아가지 않는다.
        // 복구된 상태가 PENDING(프로필 미설정)이면 회원은 /signup을 다시 호출해 signupToken을 재발급받아 프로필 설정을 이어가면 된다.
        MemberStatus status = member.getStatus();
        boolean restored = member.isRestorable();
        if (restored) {
            status = memberCommandService.restore(member.getId());
            log.info("탈퇴 유예 기간 내 재로그인으로 계정 복구: memberId={}, restoredStatus={}", member.getId(), status);
        }

        if (status != MemberStatus.ACTIVE) {
            log.warn("ACTIVE 상태가 아닌 회원의 로그인 시도: memberId={}, status={}", member.getId(), status);
            throw new CustomException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        IssuedTokens tokens = authTokenIssuer.issue(member.getId());

        log.info("로그인 성공: memberId={}, restored={}", member.getId(), restored);
        return new LoginResult(member.getId(), tokens.accessToken(), tokens.refreshToken(), restored, member.getRole());
    }

    /**
     * refreshToken 검증 -> rotation(reuse detection 포함) -> accessToken/refreshToken 재발급
     */
    public ReissueResult reissue(String refreshToken) {

        Claims claims = parseRefreshClaims(refreshToken);
        Long memberId = extractMemberId(claims);

        String familyId = claims.get(AuthTokenClaims.FAMILY_ID_KEY, String.class);
        String jti = claims.get(AuthTokenClaims.JTI_KEY, String.class);
        if (familyId == null || jti == null) {
            // rotation 도입 이전에 발급된 refreshToken - 세션 개념이 없으므로 재로그인을 유도한다.
            log.warn("familyId/jti 클레임이 없는 refreshToken으로 accessToken 재발급 시도: memberId={}", memberId);
            throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 회원의 refreshToken으로 accessToken 재발급 시도: memberId={}", memberId);
                    return new CustomException(AuthErrorCode.MEMBER_NOT_FOUND);
                });

        // 탈퇴/정지 이후에도 만료 전 refreshToken은 서명 검증만으로는 걸러지지 않으므로, 로그인과 동일하게 DB 상태를 다시 확인한다.
        if (member.getStatus() != MemberStatus.ACTIVE) {
            log.warn("ACTIVE 상태가 아닌 회원의 accessToken 재발급 시도: memberId={}, status={}", member.getId(), member.getStatus());
            throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String rotatedJti = rotateSession(familyId, jti, memberId);
        IssuedTokens tokens = authTokenIssuer.reissue(member.getId(), familyId, rotatedJti);

        log.info("accessToken 재발급 성공: memberId={}, familyId={}", member.getId(), familyId);
        return new ReissueResult(member.getId(), tokens.accessToken(), tokens.refreshToken(), member.getRole());
    }

    /**
     * 로그아웃 - refreshToken의 familyId로 세션을 삭제한다.
     * 항상 성공(멱등)하며, 토큰이 만료·위변조되었더라도 예외를 던지지 않는다.
     */
    public void logout(String refreshToken) {

        Claims claims;
        try {
            claims = jwtProvider.parseClaims(refreshToken);
        } catch (ExpiredJwtException e) {
            // 서명은 유효했으므로 만료된 토큰이어도 claims를 신뢰해 세션을 정리한다 (로그아웃은 만료 여부를 따지지 않는다).
            claims = e.getClaims();
        } catch (JwtException e) {
            log.warn("위변조되었거나 형식이 잘못된 refreshToken으로 로그아웃 시도");
            return;
        }

        // 다른 용도의 토큰을 쿠키에 넣어 보낸 경우를 막는다 (AuthTokenClaims 참고)
        if (!AuthTokenClaims.REFRESH_PURPOSE.equals(claims.get(AuthTokenClaims.PURPOSE_KEY, String.class))) {
            log.warn("purpose가 REFRESH가 아닌 토큰으로 로그아웃 시도");
            return;
        }

        String familyId = claims.get(AuthTokenClaims.FAMILY_ID_KEY, String.class);
        if (familyId == null) {
            log.warn("familyId 클레임이 없는 refreshToken으로 로그아웃 시도");
            return;
        }

        refreshSessionRepository.delete(familyId);

        Long memberId = parseMemberIdOrNull(claims);
        if (memberId != null) {
            refreshSessionRepository.removeFromMemberIndex(memberId, familyId);
        }

        log.info("로그아웃 성공: memberId={}, familyId={}", memberId, familyId);
    }

    // 로그아웃은 어떤 입력에도 실패하지 않아야 하므로 subject가 깨져 있으면 예외 대신 null을 돌려준다.
    private Long parseMemberIdOrNull(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("subject가 memberId 형식이 아닌 refreshToken으로 로그아웃 시도");
            return null;
        }
    }

    /**
     * 서명·만료 검증에 더해 purpose가 REFRESH인지까지 확인한다.
     * purpose 검증은 accessToken/signupToken을 refreshToken 자리에 잘못 흘려넣는 걸 막는다 (AuthTokenClaims 참고).
     */
    private Claims parseRefreshClaims(String refreshToken) {
        Claims claims;
        try {
            claims = jwtProvider.parseClaims(refreshToken);
        } catch (ExpiredJwtException e) {
            log.warn("만료된 refreshToken으로 accessToken 재발급 시도");
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        } catch (JwtException e) {
            log.warn("위변조되었거나 형식이 잘못된 refreshToken으로 accessToken 재발급 시도");
            throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!AuthTokenClaims.REFRESH_PURPOSE.equals(claims.get(AuthTokenClaims.PURPOSE_KEY, String.class))) {
            log.warn("purpose가 REFRESH가 아닌 토큰으로 accessToken 재발급 시도");
            throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        return claims;
    }

    private Long extractMemberId(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            log.warn("subject가 memberId 형식이 아닌 refreshToken으로 accessToken 재발급 시도");
            throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    /**
     * 세션의 jti를 회전시키고 이번 요청이 사용할 jti를 반환한다.
     * 회전할 수 없는 상태(세션 없음·재사용 탐지·소유자 불일치)면 예외를 던진다.
     */
    private String rotateSession(String familyId, String jti, Long memberId) {
        RefreshSessionRepository.RotateResult result =
                refreshSessionRepository.rotate(familyId, jti, UUID.randomUUID().toString(), memberId);

        switch (result.status()) {
            case NOT_FOUND -> {
                log.warn("이미 로그아웃되었거나 만료된 세션의 refreshToken으로 accessToken 재발급 시도: memberId={}, familyId={}", memberId, familyId);
                throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
            case REUSE_DETECTED -> {
                log.error("refreshToken 재사용 탐지(탈취 의심) - 세션 강제 종료: memberId={}, familyId={}", memberId, familyId);
                throw new CustomException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
            }
            case MEMBER_MISMATCH -> {
                // 서명이 유효한 토큰의 subject와 세션 소유자가 다른 경우 - 정상 흐름에서는 발생할 수 없다.
                log.error("refreshToken subject와 세션 소유자 불일치 - 세션 강제 종료: memberId={}, familyId={}", memberId, familyId);
                throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
            case GRACE -> log.info("동시 reissue 요청으로 판단해 현재 세션 토큰을 그대로 재사용: memberId={}, familyId={}", memberId, familyId);
            case OK -> log.info("refreshToken rotate 성공: memberId={}, familyId={}", memberId, familyId);
        }
        return result.jti();
    }
}