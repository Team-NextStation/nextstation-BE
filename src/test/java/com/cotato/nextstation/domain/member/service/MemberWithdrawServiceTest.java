package com.cotato.nextstation.domain.member.service;

import com.cotato.nextstation.domain.auth.repository.RefreshSessionRepository;
import com.cotato.nextstation.domain.member.exception.MemberErrorCode;
import com.cotato.nextstation.domain.member.service.command.MemberCommandService;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawServiceTest {

    @InjectMocks
    private MemberWithdrawService memberWithdrawService;

    @Mock
    private MemberCommandService memberCommandService;

    @Mock
    private RefreshSessionRepository refreshSessionRepository;

    @Test
    @DisplayName("DB 처리를 마친 뒤에 세션을 삭제한다")
    void withdraw_dbBeforeRedis() {
        // when
        memberWithdrawService.withdraw(1L);

        // then
        InOrder inOrder = inOrder(memberCommandService, refreshSessionRepository);
        inOrder.verify(memberCommandService).withdraw(1L);
        inOrder.verify(refreshSessionRepository).deleteAllOf(1L);
    }

    @Test
    @DisplayName("세션 삭제가 실패해도 예외를 올리지 않는다 - 탈퇴는 이미 커밋됐으므로 실패 응답을 주면 안 된다")
    void withdraw_swallowsSessionDeletionFailure() {
        // given
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(refreshSessionRepository).deleteAllOf(1L);

        // when & then
        assertThatCode(() -> memberWithdrawService.withdraw(1L))
                .doesNotThrowAnyException();

        then(memberCommandService).should().withdraw(1L);
    }

    @Test
    @DisplayName("DB 처리가 실패하면 세션을 지우지 않는다 - 탈퇴는 안 됐는데 로그아웃만 되는 상태를 막는다")
    void withdraw_keepsSessionsWhenDbFails() {
        // given
        willThrow(new CustomException(MemberErrorCode.MEMBER_NOT_FOUND))
                .given(memberCommandService).withdraw(1L);

        // when & then
        assertThatThrownBy(() -> memberWithdrawService.withdraw(1L))
                .isInstanceOf(CustomException.class);

        then(refreshSessionRepository).should(never()).deleteAllOf(1L);
    }
}
