package com.cotato.nextstation.domain.report.service;

import com.cotato.nextstation.domain.course.repository.CourseRepository;
import com.cotato.nextstation.domain.journal.repository.JournalRepository;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import com.cotato.nextstation.domain.recommendation.enums.TravelTime;
import com.cotato.nextstation.domain.recommendation.repository.RecommendationLogRepository;
import com.cotato.nextstation.domain.recommendation.repository.RecommendationLogRepository.NameCountView;
import com.cotato.nextstation.domain.recommendation.repository.RecommendationLogRepository.TravelTimeCountView;
import com.cotato.nextstation.domain.report.client.DiscordWebhookClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 서비스 지표를 집계해 디스코드로 리포트를 전송한다. 일간·주간은 집계 구간과 라벨만 다르다.
 */
@Slf4j
@Profile("prod")
@Component
@RequiredArgsConstructor
public class ServiceReportSender {

    private static final int TOP_STATION_LIMIT = 5;
    private static final int TOP_CONDITION_LIMIT = 3;
    private static final String TRAVEL_STYLE_DELIMITER = ",";
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("M월 d일");
    private static final int EMBED_COLOR = 0x3B82F6;
    private static final int DESCRIPTION_MAX_LENGTH = 4096;

    private final RecommendationLogRepository recommendationLogRepository;
    private final MemberRepository memberRepository;
    private final CourseRepository courseRepository;
    private final JournalRepository journalRepository;
    private final DiscordWebhookClient discordWebhookClient;

    @Scheduled(cron = "${report.cron.daily:0 0 9 * * *}")
    public void sendDailyReport() {
        LocalDate today = LocalDate.now();
        sendReport("일간", today.minusDays(1).atStartOfDay(), today.atStartOfDay());
    }

    // 일간 리포트와 전송 시각이 겹치지 않게 10분 늦춘다.
    @Scheduled(cron = "${report.cron.weekly:0 10 9 * * MON}")
    public void sendWeeklyReport() {
        LocalDate thisMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        sendReport("주간", thisMonday.minusWeeks(1).atStartOfDay(), thisMonday.atStartOfDay());
    }

    /**
     * 집계 구간은 [from, to). 일간·주간 리포트가 같은 건을 중복 집계하지 않도록 끝을 배제한다.
     * <p>
     * 트랜잭션을 열지 않는다. 웹훅 호출이 트랜잭션 안에 들어가면 응답이 느릴 때 커넥션을 그만큼 붙잡는다.
     */
    private void sendReport(String label, LocalDateTime from, LocalDateTime to) {
        log.info("{} 리포트 집계 시작: from={}, to={}", label, from, to);

        long customCount = recommendationLogRepository.countByIsRandomInPeriod(false, from, to);
        long randomCount = recommendationLogRepository.countByIsRandomInPeriod(true, from, to);
        Map<String, Long> topResultStations = toRanking(recommendationLogRepository.findTopResultStations(
                from, to, PageRequest.of(0, TOP_STATION_LIMIT)));
        Map<String, Long> topDepartureStations = toRanking(recommendationLogRepository.findTopDepartureStations(
                from, to, PageRequest.of(0, TOP_CONDITION_LIMIT)));
        String travelTimes = formatTravelTimes(recommendationLogRepository.countByTravelTimeInPeriod(from, to));
        Map<String, Long> topTravelStyles = countTravelStyles(
                recommendationLogRepository.findTravelStylesInPeriod(from, to));
        long courseCount = courseRepository.countCreatedInPeriod(from, to);
        long journalCount = journalRepository.countCreatedInPeriod(from, to);
        long joinedCount = memberRepository.countJoinedInPeriod(from, to);
        long totalMemberCount = memberRepository.countByStatusNot(MemberStatus.WITHDRAWN);

        String description = """
                **👤 회원**
                신규 %d · 누적 %d

                **🎯 추천**
                %d회 (맞춤 %d / 랜덤 %d)

                **💾 활동**
                코스 저장 %d · 여행일지 %d

                **📍 추천된 역 TOP %d**
                %s

                **🚇 출발역 TOP %d**
                %s

                **⏱️ 이동 가능 시간**
                %s

                **🏷️ 선택 태그 TOP %d**
                %s

                **📈 퍼널**
                %s"""
                .formatted(joinedCount, totalMemberCount,
                        customCount + randomCount, customCount, randomCount,
                        courseCount, journalCount,
                        TOP_STATION_LIMIT, formatRanking(topResultStations),
                        TOP_CONDITION_LIMIT, formatRanking(topDepartureStations),
                        travelTimes,
                        TOP_CONDITION_LIMIT, formatRanking(topTravelStyles),
                        formatFunnel(customCount + randomCount, courseCount, journalCount));

        Map<String, Object> payload = Map.of("embeds", List.of(Map.of(
                "title", "🚇 환승여행 %s 리포트 · %s".formatted(label, formatPeriod(from, to)),
                "color", EMBED_COLOR,
                "description", truncate(description))));
        log.info("{} 리포트 집계 완료: custom={}, random={}, course={}, journal={}, joined={}",
                label, customCount, randomCount, courseCount, journalCount, joinedCount);

        discordWebhookClient.send(payload);
    }

    // 길이 제한을 넘기면 임베드 전체가 거부된다.
    private String truncate(String description) {
        return description.length() <= DESCRIPTION_MAX_LENGTH
                ? description
                : description.substring(0, DESCRIPTION_MAX_LENGTH);
    }

    // 전환율은 바로 앞 단계 대비
    private String formatFunnel(long recommendCount, long courseCount, long journalCount) {
        return "추천 %d → 저장 %d %s → 여행일지 %d %s".formatted(
                recommendCount,
                courseCount, formatConversion(courseCount, recommendCount),
                journalCount, formatConversion(journalCount, courseCount));
    }

    private String formatConversion(long count, long previousCount) {
        return previousCount == 0 ? "(-)" : "(%d%%)".formatted(Math.round(count * 100.0 / previousCount));
    }

    private Map<String, Long> toRanking(List<NameCountView> rows) {
        return rows.stream().collect(Collectors.toMap(
                NameCountView::getName, NameCountView::getCount, (first, second) -> first, LinkedHashMap::new));
    }

    // 선택 0건인 값도 찍는다. 있는 것만 내보내면 항목 순서와 개수가 날마다 달라진다.
    private String formatTravelTimes(List<TravelTimeCountView> rows) {
        Map<TravelTime, Long> countByTravelTime = rows.stream()
                .collect(Collectors.toMap(TravelTimeCountView::getTravelTime, TravelTimeCountView::getCount));
        return Arrays.stream(TravelTime.values())
                .map(travelTime -> "%s %d".formatted(
                        travelTimeLabel(travelTime), countByTravelTime.getOrDefault(travelTime, 0L)))
                .collect(Collectors.joining(" / "));
    }

    private String travelTimeLabel(TravelTime travelTime) {
        return switch (travelTime) {
            case THIRTY_MINUTES -> "30분 이내";
            case ONE_HOUR -> "1시간 이내";
            case ANY -> "상관없음";
        };
    }

    // 태그는 콤마로 join된 한 컬럼이라 DB에서 GROUP BY 불가능
    private Map<String, Long> countTravelStyles(List<String> joinedTravelStyles) {
        return joinedTravelStyles.stream()
                .flatMap(styles -> Arrays.stream(styles.split(TRAVEL_STYLE_DELIMITER)))
                .collect(Collectors.groupingBy(this::travelStyleLabel, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_CONDITION_LIMIT)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, LinkedHashMap::new));
    }

    // enum에서 사라진 과거 태그가 로그에 남아 있을 수 있다.
    private String travelStyleLabel(String travelStyle) {
        try {
            return PlaceTagName.valueOf(travelStyle).getLabel();
        } catch (IllegalArgumentException e) {
            return travelStyle;
        }
    }

    // to는 구간에 포함되지 않으므로 표기는 하루 앞을 마지막 날로 쓴다.
    private String formatPeriod(LocalDateTime from, LocalDateTime to) {
        LocalDate firstDate = from.toLocalDate();
        LocalDate lastDate = to.toLocalDate().minusDays(1);
        return firstDate.equals(lastDate)
                ? firstDate.format(PERIOD_FORMATTER)
                : firstDate.format(PERIOD_FORMATTER) + " ~ " + lastDate.format(PERIOD_FORMATTER);
    }

    private String formatRanking(Map<String, Long> ranking) {
        if (ranking.isEmpty()) {
            return "(없음)";
        }
        return ranking.entrySet().stream()
                .map(entry -> "%s %d".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(" / "));
    }
}
