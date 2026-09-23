package com.cotato.nextstation.domain.block.service.query;

import com.cotato.nextstation.domain.block.dto.response.BlockedMemberListResponse;
import com.cotato.nextstation.domain.block.dto.response.BlockedMemberResponse;
import com.cotato.nextstation.domain.block.repository.MemberBlockRepository;
import com.cotato.nextstation.domain.block.repository.MemberBlockRepository.BlockedMemberView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MemberBlockQueryServiceTest {

    @InjectMocks
    private MemberBlockQueryService memberBlockQueryService;

    @Mock
    private MemberBlockRepository memberBlockRepository;

    private BlockedMemberView blockedMemberView(Long memberId, String nickname, String profileImageUrl, LocalDateTime blockedAt) {
        BlockedMemberView view = mock(BlockedMemberView.class);
        given(view.getMemberId()).willReturn(memberId);
        given(view.getNickname()).willReturn(nickname);
        given(view.getProfileImageUrl()).willReturn(profileImageUrl);
        given(view.getBlockedAt()).willReturn(blockedAt);
        return view;
    }

    @Test
    @DisplayName("차단한 사용자 목록을 조회하면 리포지토리 결과가 그대로 응답으로 변환된다")
    void getBlockedMembers_success() {
        // given
        Long blockerId = 1L;
        LocalDateTime blockedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        BlockedMemberView view = blockedMemberView(2L, "차단당한사람", "https://image.url", blockedAt);
        given(memberBlockRepository.findBlockedMembers(blockerId)).willReturn(List.of(view));

        // when
        BlockedMemberListResponse response = memberBlockQueryService.getBlockedMembers(blockerId);

        // then
        assertThat(response.members()).hasSize(1);
        BlockedMemberResponse memberResponse = response.members().get(0);
        assertThat(memberResponse.memberId()).isEqualTo(2L);
        assertThat(memberResponse.nickname()).isEqualTo("차단당한사람");
        assertThat(memberResponse.profileImageUrl()).isEqualTo("https://image.url");
        assertThat(memberResponse.blockedAt()).isEqualTo(blockedAt);
    }

    @Test
    @DisplayName("차단한 사용자가 없으면 빈 목록을 반환한다")
    void getBlockedMembers_empty() {
        // given
        given(memberBlockRepository.findBlockedMembers(1L)).willReturn(List.of());

        // when
        BlockedMemberListResponse response = memberBlockQueryService.getBlockedMembers(1L);

        // then
        assertThat(response.members()).isEmpty();
    }
}
