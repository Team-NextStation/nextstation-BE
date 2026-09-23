package com.cotato.nextstation.domain.stamp.service.query;

import com.cotato.nextstation.domain.block.repository.MemberBlockRepository;
import com.cotato.nextstation.domain.member.exception.MemberErrorCode;
import com.cotato.nextstation.domain.member.service.query.MemberExistenceQueryService;
import com.cotato.nextstation.domain.stamp.converter.MemberStampConverter;
import com.cotato.nextstation.domain.stamp.dto.response.MemberStampListResponse;
import com.cotato.nextstation.domain.stamp.dto.response.MemberStampResponse;
import com.cotato.nextstation.domain.stamp.dto.response.MyStampDetailResponse;
import com.cotato.nextstation.domain.stamp.dto.response.MyStampListResponse;
import com.cotato.nextstation.domain.stamp.exception.StampErrorCode;
import com.cotato.nextstation.domain.stamp.repository.MemberStampRepository;
import com.cotato.nextstation.domain.stamp.repository.MemberStampRepository.MyStampDetailView;
import com.cotato.nextstation.domain.stamp.repository.MemberStampRepository.VisitedStationView;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import com.cotato.nextstation.domain.station.entity.LineCode;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberStampQueryServiceTest {

    @InjectMocks
    private MemberStampQueryService memberStampQueryService;

    @Mock
    private MemberStampRepository memberStampRepository;

    @Mock
    private MemberExistenceQueryService memberExistenceQueryService;

    @Mock
    private MemberStampConverter memberStampConverter;

    @Mock
    private MemberBlockRepository memberBlockRepository;

    private VisitedStationView stampView(Long stationId, String stationName, Long lineId, String lineName, LineCode lineCode) {
        VisitedStationView view = mock(VisitedStationView.class);
        lenient().when(view.getStationId()).thenReturn(stationId);
        lenient().when(view.getStationName()).thenReturn(stationName);
        lenient().when(view.getLineId()).thenReturn(lineId);
        lenient().when(view.getLineName()).thenReturn(lineName);
        lenient().when(view.getLineCode()).thenReturn(lineCode);
        return view;
    }

    @Test
    @DisplayName("완료한 코스 id만 집합으로 반환한다")
    void getCompletedCourseIds_returnsOnlyCompleted() {
        // given: 3개를 물어봤는데 2개만 완료된 상태
        given(memberStampRepository.findCompletedCourseIds(1L, List.of(10L, 20L, 30L)))
                .willReturn(List.of(10L, 30L));

        // when
        Set<Long> completed = memberStampQueryService.getCompletedCourseIds(1L, List.of(10L, 20L, 30L));

        // then
        assertThat(completed).containsExactlyInAnyOrder(10L, 30L);
    }

    @Test
    @DisplayName("완료한 코스가 없으면 빈 집합을 반환한다")
    void getCompletedCourseIds_noneCompleted() {
        // given
        given(memberStampRepository.findCompletedCourseIds(1L, List.of(10L))).willReturn(List.of());

        // when
        Set<Long> completed = memberStampQueryService.getCompletedCourseIds(1L, List.of(10L));

        // then
        assertThat(completed).isEmpty();
    }

    @Test
    @DisplayName("memberStampId가 null이면 조회 없이 바로 404를 던진다")
    void getCourseId_nullMemberStampId_throwsWithoutQuery() {
        // when & then: JpaRepository.findById(null)은 Optional.empty()가 아니라 예외를 던지므로
        // 그대로 두면 시드 데이터처럼 memberStampId가 없는 경우 500이 된다. 미리 걸러야 한다.
        assertThatThrownBy(() -> memberStampQueryService.getCourseId(1L, null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(StampErrorCode.MEMBER_STAMP_NOT_FOUND.getMessage());
        verify(memberStampRepository, never()).findById(any());
    }

    @Test
    @DisplayName("코스 목록이 비어 있으면 조회하지 않고 빈 집합을 반환한다")
    void getCompletedCourseIds_emptyInputSkipsQuery() {
        // when: 코스가 없는 페이지에서 IN () 쿼리를 날릴 이유가 없다
        Set<Long> completed = memberStampQueryService.getCompletedCourseIds(1L, List.of());

        // then
        assertThat(completed).isEmpty();
        verify(memberStampRepository, never()).findCompletedCourseIds(anyLong(), any());
    }

    @Test
    @DisplayName("방문한 서로 다른 역의 개수를 그대로 반환한다")
    void getStampCount_returnsVisitedStationCount() {
        // given
        given(memberStampRepository.countVisitedStations(1L)).willReturn(12L);

        // when
        long stampCount = memberStampQueryService.getStampCount(1L);

        // then
        assertThat(stampCount).isEqualTo(12L);
    }

    @Test
    @DisplayName("존재하는 회원이면 역 목록을 스탬프 목록으로 반환한다")
    void getMemberStamps_success() {
        // given
        VisitedStationView view = stampView(6L, "보문역", 6L, "6호선", LineCode.LINE_6);
        given(memberExistenceQueryService.existsMember(2L)).willReturn(true);
        given(memberStampRepository.findVisitedStationsByMemberId(2L)).willReturn(List.of(view));

        // when
        MemberStampListResponse response = memberStampQueryService.getMemberStamps(1L, 2L);

        // then
        assertThat(response.stampCount()).isEqualTo(1);
        assertThat(response.stamps()).containsExactly(
                new MemberStampResponse(6L, "보문역", new LineSummaryResponse(6L, "6호선", LineCode.LINE_6)));
    }

    @Test
    @DisplayName("1호선 → 9호선 순으로 정렬하고, 대표 호선이 없는 역은 맨 뒤로 보낸다")
    void getMemberStamps_sortsByLineOrderWithNoLineLast() {
        // given: 일부러 뒤섞어서 넘긴다 (3호선, 노선없음, 1호선)
        VisitedStationView line3 = stampView(3L, "역C", 3L, "3호선", LineCode.LINE_3);
        VisitedStationView noLine = stampView(2L, "역B", null, null, null);
        VisitedStationView line1 = stampView(1L, "역A", 1L, "1호선", LineCode.LINE_1);
        given(memberExistenceQueryService.existsMember(2L)).willReturn(true);
        given(memberStampRepository.findVisitedStationsByMemberId(2L)).willReturn(List.of(line3, noLine, line1));

        // when
        MemberStampListResponse response = memberStampQueryService.getMemberStamps(1L, 2L);

        // then
        assertThat(response.stamps()).extracting(MemberStampResponse::stationId)
                .containsExactly(1L, 3L, 2L);
    }

    @Test
    @DisplayName("동일 호선 내에서는 역명 가나다순으로 정렬한다")
    void getMemberStamps_sortsByStationNameWithinSameLine() {
        // given: 같은 2호선인데 역명 순서를 뒤섞어서 넘긴다
        VisitedStationView na = stampView(2L, "나역", 2L, "2호선", LineCode.LINE_2);
        VisitedStationView da = stampView(3L, "다역", 2L, "2호선", LineCode.LINE_2);
        VisitedStationView ga = stampView(1L, "가역", 2L, "2호선", LineCode.LINE_2);
        given(memberExistenceQueryService.existsMember(2L)).willReturn(true);
        given(memberStampRepository.findVisitedStationsByMemberId(2L)).willReturn(List.of(na, da, ga));

        // when
        MemberStampListResponse response = memberStampQueryService.getMemberStamps(1L, 2L);

        // then
        assertThat(response.stamps()).extracting(MemberStampResponse::stationName)
                .containsExactly("가역", "나역", "다역");
    }

    @Test
    @DisplayName("존재하지 않는 회원의 스탬프 목록을 조회하면 예외가 발생한다")
    void getMemberStamps_memberNotFound() {
        // given
        given(memberExistenceQueryService.existsMember(2L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> memberStampQueryService.getMemberStamps(1L, 2L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("1호선 → 9호선 순으로 정렬하고, 대표 호선이 없는 역은 맨 뒤로 보낸다")
    void getMyStamps_sortsByLineOrder_nullLineLast() {
        // given: 리포지토리 응답 순서와 무관하게 노선 순으로 재정렬돼야 한다
        VisitedStationView line6 = stampView(1L, "역F", null, null, LineCode.LINE_6);
        VisitedStationView noLine = stampView(2L, "역N", null, null, null);
        VisitedStationView line1 = stampView(3L, "역A", null, null, LineCode.LINE_1);

        given(memberStampRepository.findVisitedStationsByMemberId(1L))
                .willReturn(List.of(line6, noLine, line1));
        given(memberStampConverter.toMyStampListResponse(any())).willReturn(mock(MyStampListResponse.class));

        // when
        memberStampQueryService.getMyStamps(1L);

        // then
        ArgumentCaptor<List<VisitedStationView>> captor = ArgumentCaptor.forClass(List.class);
        verify(memberStampConverter).toMyStampListResponse(captor.capture());

        assertThat(captor.getValue())
                .extracting(VisitedStationView::getStationId)
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    @DisplayName("동일 호선 내에서는 역명 가나다순으로 정렬한다")
    void getMyStamps_sortsByStationNameWithinSameLine() {
        // given: 같은 2호선인데 역명 순서를 뒤섞어서 넘긴다
        VisitedStationView na = stampView(2L, "나역", null, null, LineCode.LINE_2);
        VisitedStationView da = stampView(3L, "다역", null, null, LineCode.LINE_2);
        VisitedStationView ga = stampView(1L, "가역", null, null, LineCode.LINE_2);

        given(memberStampRepository.findVisitedStationsByMemberId(1L))
                .willReturn(List.of(na, da, ga));
        given(memberStampConverter.toMyStampListResponse(any())).willReturn(mock(MyStampListResponse.class));

        // when
        memberStampQueryService.getMyStamps(1L);

        // then
        ArgumentCaptor<List<VisitedStationView>> captor = ArgumentCaptor.forClass(List.class);
        verify(memberStampConverter).toMyStampListResponse(captor.capture());

        assertThat(captor.getValue())
                .extracting(VisitedStationView::getStationName)
                .containsExactly("가역", "나역", "다역");
    }

    @Test
    @DisplayName("컨버터가 만든 응답을 그대로 반환한다")
    void getMyStamps_returnsConverterResponse() {
        // given
        given(memberStampRepository.findVisitedStationsByMemberId(1L)).willReturn(List.of());
        MyStampListResponse expected = mock(MyStampListResponse.class);
        given(memberStampConverter.toMyStampListResponse(any())).willReturn(expected);

        // when
        MyStampListResponse response = memberStampQueryService.getMyStamps(1L);

        // then
        assertThat(response).isSameAs(expected);
    }

    @Test
    @DisplayName("해당 역의 최초 방문 스탬프 상세와, 이 역에서 가장 이른 여행일지 id를 컨버터로 변환해 반환한다")
    void getMyStampDetail_success() {
        // given
        MyStampDetailView view = mock(MyStampDetailView.class);
        given(memberStampRepository.findEarliestStampByMemberIdAndStationId(eq(1L), eq(5L), any(Pageable.class)))
                .willReturn(List.of(view));
        given(memberStampRepository.findEarliestJournalIdByMemberIdAndStationId(eq(1L), eq(5L), any(Pageable.class)))
                .willReturn(List.of(42L));
        MyStampDetailResponse expected = mock(MyStampDetailResponse.class);
        given(memberStampConverter.toMyStampDetailResponse(view, 42L)).willReturn(expected);

        // when
        MyStampDetailResponse response = memberStampQueryService.getMyStampDetail(1L, 5L);

        // then
        assertThat(response).isSameAs(expected);
    }

    @Test
    @DisplayName("최초 완주 건에는 일지가 없어도, 이후 재완주 때 쓴 일지가 있으면 그 journalId를 사용한다")
    void getMyStampDetail_usesJournalFromLaterCompletionWhenFirstHasNone() {
        // given
        MyStampDetailView view = mock(MyStampDetailView.class);
        given(memberStampRepository.findEarliestStampByMemberIdAndStationId(eq(1L), eq(5L), any(Pageable.class)))
                .willReturn(List.of(view));
        given(memberStampRepository.findEarliestJournalIdByMemberIdAndStationId(eq(1L), eq(5L), any(Pageable.class)))
                .willReturn(List.of(99L));
        MyStampDetailResponse expected = mock(MyStampDetailResponse.class);
        given(memberStampConverter.toMyStampDetailResponse(view, 99L)).willReturn(expected);

        // when
        MyStampDetailResponse response = memberStampQueryService.getMyStampDetail(1L, 5L);

        // then
        assertThat(response).isSameAs(expected);
    }

    @Test
    @DisplayName("이 역에서 작성된 여행일지가 하나도 없으면 journalId는 null로 전달된다")
    void getMyStampDetail_nullJournalIdWhenNoJournalExists() {
        // given
        MyStampDetailView view = mock(MyStampDetailView.class);
        given(memberStampRepository.findEarliestStampByMemberIdAndStationId(eq(1L), eq(5L), any(Pageable.class)))
                .willReturn(List.of(view));
        given(memberStampRepository.findEarliestJournalIdByMemberIdAndStationId(eq(1L), eq(5L), any(Pageable.class)))
                .willReturn(List.of());
        MyStampDetailResponse expected = mock(MyStampDetailResponse.class);
        given(memberStampConverter.toMyStampDetailResponse(view, null)).willReturn(expected);

        // when
        MyStampDetailResponse response = memberStampQueryService.getMyStampDetail(1L, 5L);

        // then
        assertThat(response).isSameAs(expected);
    }

    @Test
    @DisplayName("해당 역에 방문 기록이 없으면 MEMBER_STAMP_NOT_FOUND 예외가 발생한다")
    void getMyStampDetail_notFound() {
        // given
        given(memberStampRepository.findEarliestStampByMemberIdAndStationId(eq(1L), eq(5L), any(Pageable.class)))
                .willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> memberStampQueryService.getMyStampDetail(1L, 5L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(StampErrorCode.MEMBER_STAMP_NOT_FOUND.getMessage());
    }

}
