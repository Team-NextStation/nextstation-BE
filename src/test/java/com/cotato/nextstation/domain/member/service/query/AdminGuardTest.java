package com.cotato.nextstation.domain.member.service.query;

import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberRole;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminGuardTest {

    private static final Long MEMBER_ID = 1L;

    @InjectMocks
    private AdminGuard adminGuard;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("ACTIVE 상태의 ADMIN은 통과한다")
    void requireAdmin_passesForActiveAdmin() {
        // given
        given(memberRepository.findById(MEMBER_ID))
                .willReturn(Optional.of(member(MemberRole.ADMIN, MemberStatus.ACTIVE)));

        // when & then
        assertThatCode(() -> adminGuard.requireAdmin(MEMBER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일반 회원은 403이다")
    void requireAdmin_rejectsUser() {
        // given
        given(memberRepository.findById(MEMBER_ID))
                .willReturn(Optional.of(member(MemberRole.USER, MemberStatus.ACTIVE)));

        // when & then
        assertForbidden();
    }

    @Test
    @DisplayName("ADMIN이어도 정지된 계정은 403이다 - 권한 회수보다 계정 정지가 먼저 이뤄질 수 있다")
    void requireAdmin_rejectsSuspendedAdmin() {
        // given
        given(memberRepository.findById(MEMBER_ID))
                .willReturn(Optional.of(member(MemberRole.ADMIN, MemberStatus.SUSPENDED)));

        // when & then
        assertForbidden();
    }

    @Test
    @DisplayName("존재하지 않는 회원은 404가 아니라 403이다 - 계정 존재 여부를 노출하지 않는다")
    void requireAdmin_rejectsUnknownMemberWithForbidden() {
        // given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // when & then
        assertForbidden();
    }

    private void assertForbidden() {
        assertThatThrownBy(() -> adminGuard.requireAdmin(MEMBER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    private Member member(MemberRole role, MemberStatus status) {
        Member member = Member.builder().email("admin@nextstation.com").password("encoded").build();
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        ReflectionTestUtils.setField(member, "role", role);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }
}
