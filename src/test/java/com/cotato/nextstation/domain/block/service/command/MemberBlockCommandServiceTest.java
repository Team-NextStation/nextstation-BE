package com.cotato.nextstation.domain.block.service.command;

import com.cotato.nextstation.domain.block.entity.MemberBlock;
import com.cotato.nextstation.domain.block.exception.MemberBlockErrorCode;
import com.cotato.nextstation.domain.block.repository.MemberBlockRepository;
import com.cotato.nextstation.domain.member.exception.MemberErrorCode;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberBlockCommandServiceTest {

    @InjectMocks
    private MemberBlockCommandService memberBlockCommandService;

    @Mock
    private MemberBlockRepository memberBlockRepository;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("사용자를 차단하면 blocker/blocked가 정확히 저장된다")
    void block_success() {
        // given: blockerId와 blockedId를 다른 값으로 둬야 둘이 뒤바뀌는 실수를 잡을 수 있다
        Long blockerId = 1L;
        Long blockedId = 2L;
        given(memberRepository.existsById(blockedId)).willReturn(true);
        given(memberBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)).willReturn(false);

        // when
        memberBlockCommandService.block(blockerId, blockedId);

        // then
        ArgumentCaptor<MemberBlock> captor = ArgumentCaptor.forClass(MemberBlock.class);
        verify(memberBlockRepository).save(captor.capture());
        assertThat(captor.getValue().getBlockerId()).isEqualTo(blockerId);
        assertThat(captor.getValue().getBlockedId()).isEqualTo(blockedId);
    }

    @Test
    @DisplayName("자기 자신은 차단할 수 없다")
    void block_selfBlockNotAllowed() {
        // when & then
        assertThatThrownBy(() -> memberBlockCommandService.block(1L, 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberBlockErrorCode.SELF_BLOCK_NOT_ALLOWED.getMessage());
        verify(memberRepository, never()).existsById(any());
        verify(memberBlockRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 회원은 차단할 수 없다")
    void block_memberNotFound() {
        // given
        given(memberRepository.existsById(2L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> memberBlockCommandService.block(1L, 2L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
        verify(memberBlockRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 차단한 사용자를 다시 차단하면 예외가 발생한다")
    void block_duplicate() {
        // given
        given(memberRepository.existsById(2L)).willReturn(true);
        given(memberBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberBlockCommandService.block(1L, 2L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberBlockErrorCode.ALREADY_BLOCKED.getMessage());
        verify(memberBlockRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시 요청으로 유니크 제약에 걸리면 중복 차단 예외로 응답한다")
    void block_raceCondition() {
        // given: 중복 확인은 통과했지만 저장 시점에 다른 요청이 먼저 커밋된 상황
        given(memberRepository.existsById(2L)).willReturn(true);
        given(memberBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).willReturn(false);
        willThrow(new DataIntegrityViolationException("unique"))
                .given(memberBlockRepository).save(any(MemberBlock.class));

        // when & then
        assertThatThrownBy(() -> memberBlockCommandService.block(1L, 2L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberBlockErrorCode.ALREADY_BLOCKED.getMessage());
    }

    @Test
    @DisplayName("차단을 해제하면 레코드가 삭제된다")
    void unblock_success() {
        // given: 삭제 쿼리가 1행을 지웠다 = 실제로 차단돼 있었다
        given(memberBlockRepository.deleteByBlockerIdAndBlockedId(1L, 2L)).willReturn(1L);

        // when & then
        memberBlockCommandService.unblock(1L, 2L);
    }

    @Test
    @DisplayName("차단하지 않은 사용자를 해제하면 예외가 발생한다")
    void unblock_notBlocked() {
        // given: 지워진 행이 없다 = 차단돼 있지 않았다
        given(memberBlockRepository.deleteByBlockerIdAndBlockedId(1L, 2L)).willReturn(0L);

        // when & then
        assertThatThrownBy(() -> memberBlockCommandService.unblock(1L, 2L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberBlockErrorCode.BLOCK_NOT_FOUND.getMessage());
    }
}
