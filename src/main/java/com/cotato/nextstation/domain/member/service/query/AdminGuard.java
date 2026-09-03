package com.cotato.nextstation.domain.member.service.query;

import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberRole;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 전용 API의 진입 가드.
 * 관리자 서비스 메서드 첫 줄에서 호출해 요청자가 관리자인지 확인한다.
 * <p>
 * 권한 판정을 도메인 곳곳에 분산시키지 않기 위해 이 클래스에만 둔다.
 * <p>
 * 인증(토큰 검증, 401)은 {@code JwtPrincipalArgumentResolver}가 담당한다. 여기는 그 위에 인가(403)만 얹는다.
 * 관리자 컨트롤러가 늘어나면 {@code /api/v1/admin/**} 인터셉터로 승격하고, 그때도 이 클래스를 그대로 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminGuard {

    private final MemberRepository memberRepository;

    public void requireAdmin(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 회원의 관리자 API 접근 시도: memberId={}", memberId);
                    return new CustomException(GlobalErrorCode.FORBIDDEN);
                });

        if (member.getRole() != MemberRole.ADMIN) {
            log.warn("일반 회원의 관리자 API 접근 시도: memberId={}", memberId);
            throw new CustomException(GlobalErrorCode.FORBIDDEN);
        }

        if (member.getStatus() != MemberStatus.ACTIVE) {
            log.warn("ACTIVE 상태가 아닌 관리자의 접근 시도: memberId={}, status={}", memberId, member.getStatus());
            throw new CustomException(GlobalErrorCode.FORBIDDEN);
        }
    }
}
