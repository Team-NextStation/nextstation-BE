package com.cotato.nextstation.domain.report.service;

import com.cotato.nextstation.domain.course.repository.CourseRepository;
import com.cotato.nextstation.domain.journal.repository.JournalRepository;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.recommendation.enums.TravelTime;
import com.cotato.nextstation.domain.recommendation.repository.RecommendationLogRepository;
import com.cotato.nextstation.domain.recommendation.repository.RecommendationLogRepository.NameCountView;
import com.cotato.nextstation.domain.recommendation.repository.RecommendationLogRepository.TravelTimeCountView;
import com.cotato.nextstation.domain.report.client.DiscordWebhookClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ServiceReportSenderTest {

    @InjectMocks
    private ServiceReportSender serviceReportSender;

    @Mock
    private RecommendationLogRepository recommendationLogRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private JournalRepository journalRepository;

    @Mock
    private DiscordWebhookClient discordWebhookClient;

    @Test
    @DisplayName("일간 리포트는 전날 00:00부터 오늘 00:00 직전까지를 집계 구간으로 쓴다")
    void sendDailyReport_period() {
        // given
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        given(memberRepository.countJoinedInPeriod(fromCaptor.capture(), toCaptor.capture())).willReturn(0L);

        // when
        serviceReportSender.sendDailyReport();

        // then
        LocalDate today = LocalDate.now();
        assertThat(fromCaptor.getValue()).isEqualTo(today.minusDays(1).atStartOfDay());
        assertThat(toCaptor.getValue()).isEqualTo(today.atStartOfDay());
    }

    @Test
    @DisplayName("주간 리포트는 지난주 월요일 00:00부터 이번주 월요일 00:00 직전까지를 집계 구간으로 쓴다")
    void sendWeeklyReport_period() {
        // given
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        given(memberRepository.countJoinedInPeriod(fromCaptor.capture(), toCaptor.capture())).willReturn(0L);

        // when
        serviceReportSender.sendWeeklyReport();

        // then
        LocalDate thisMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        assertThat(fromCaptor.getValue()).isEqualTo(thisMonday.minusWeeks(1).atStartOfDay());
        assertThat(toCaptor.getValue()).isEqualTo(thisMonday.atStartOfDay());
    }

    @Test
    @DisplayName("리포트 본문에 맞춤/랜덤 건수, 결과 역 순위, 선택 조건 분포, 가입자 수가 담긴다")
    void sendDailyReport_content() {
        // given
        given(recommendationLogRepository.countByIsRandomInPeriod(anyBoolean(), any(), any()))
                .willReturn(12L, 30L);
        given(recommendationLogRepository.findTopResultStations(any(), any(), any()))
                .willReturn(List.of(nameCount("성수", 8L), nameCount("홍대입구", 5L)));
        given(recommendationLogRepository.findTopDepartureStations(any(), any(), any()))
                .willReturn(List.of(nameCount("강남", 7L), nameCount("잠실", 2L)));
        given(recommendationLogRepository.countByTravelTimeInPeriod(any(), any()))
                .willReturn(List.of(travelTimeCount(TravelTime.THIRTY_MINUTES, 9L), travelTimeCount(TravelTime.ANY, 3L)));
        given(recommendationLogRepository.findTravelStylesInPeriod(any(), any()))
                .willReturn(List.of("BUDGET,NATURE", "NATURE", "HOTPLACE,NATURE"));
        given(courseRepository.countCreatedInPeriod(any(), any())).willReturn(8L);
        given(journalRepository.countCreatedInPeriod(any(), any())).willReturn(3L);
        given(memberRepository.countJoinedInPeriod(any(), any())).willReturn(5L);
        given(memberRepository.countByStatusNot(MemberStatus.WITHDRAWN)).willReturn(128L);

        // when
        serviceReportSender.sendDailyReport();

        // then
        LocalDate yesterday = LocalDate.now().minusDays(1);
        assertThat(sentPayload())
                .contains("환승여행 일간 리포트 · %d월 %d일".formatted(yesterday.getMonthValue(), yesterday.getDayOfMonth()))
                .contains("42회 (맞춤 12 / 랜덤 30)")
                .contains("성수 8 / 홍대입구 5")
                .contains("강남 7 / 잠실 2")
                .contains("30분 이내 9 / 1시간 이내 0 / 상관없음 3")
                .contains("자연과함께 3 / 가성비 1 / 핫플레이스 1")
                .contains("신규 5 · 누적 128")
                .contains("코스 저장 8 · 여행일지 3")
                .contains("추천 42 → 저장 8 (19%) → 여행일지 3 (38%)");
    }

    @Test
    @DisplayName("태그 건수가 같으면 태그명 순으로 정렬해 실행마다 순위가 뒤집히지 않는다")
    void sendDailyReport_travelStyleTieBreak() {
        // given
        given(recommendationLogRepository.findTravelStylesInPeriod(any(), any()))
                .willReturn(List.of("NATURE,SHOPPING", "BUDGET,HOTPLACE"));
        // when
        serviceReportSender.sendDailyReport();

        // then
        assertThat(sentPayload())
                .contains("가성비 1 / 쇼핑 1 / 자연과함께 1")
                .doesNotContain("핫플레이스");
    }

    @Test
    @DisplayName("앞 단계가 0이면 전환율을 비운다")
    void sendDailyReport_funnelWithoutBase() {
        // given
        // when
        serviceReportSender.sendDailyReport();

        // then
        assertThat(sentPayload()).contains("추천 0 → 저장 0 (-) → 여행일지 0 (-)");
    }

    @Test
    @DisplayName("집계 구간에 추천 기록이 없으면 결과 역 순위를 (없음)으로 표기한다")
    void sendDailyReport_emptyTopStations() {
        // given
        given(recommendationLogRepository.findTopResultStations(any(), any(), any())).willReturn(List.of());
        // when
        serviceReportSender.sendDailyReport();

        // then
        assertThat(sentPayload()).contains("(없음)");
    }

    @SuppressWarnings("rawtypes")
    private String sentPayload() {
        ArgumentCaptor<Map> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(discordWebhookClient).send(payloadCaptor.capture());
        return payloadCaptor.getValue().toString();
    }

    private NameCountView nameCount(String name, long count) {
        return new NameCountView() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    private TravelTimeCountView travelTimeCount(TravelTime travelTime, long count) {
        return new TravelTimeCountView() {
            @Override
            public TravelTime getTravelTime() {
                return travelTime;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}
