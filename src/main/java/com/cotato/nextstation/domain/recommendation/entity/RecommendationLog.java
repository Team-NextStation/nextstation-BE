package com.cotato.nextstation.domain.recommendation.entity;

import com.cotato.nextstation.domain.recommendation.enums.TravelTime;
import com.cotato.nextstation.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Getter
@Table(
        name = "recommendation_log",
        indexes = {
                @Index(name = "idx_recommendation_log_member_created", columnList = "member_id, created_at"),
                @Index(name = "idx_recommendation_log_session_condition",
                        columnList = "session_id, is_random, departure_station_id, travel_time, travel_styles")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RecommendationLog extends BaseEntity {

    private static final String TRAVEL_STYLE_DELIMITER = ",";

    // 연관관계 매핑 대신 FK 식별자만 보관한다.
    // 비로그인 랜덤뽑기를 대비해 memberId는 nullable.
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "result_station_id", nullable = false)
    private Long resultStationId;

    @Column(name = "is_random", nullable = false)
    private boolean isRandom;

    @Column(name = "session_id", length = 36)
    private String recommendationSessionId;

    @Column(name = "departure_station_id")
    private Long departureStationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_time", length = 20)
    private TravelTime travelTime;

    @Column(name = "travel_styles", length = 50)
    private String travelStyles;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private RecommendationLog(Long memberId, Long resultStationId, boolean isRandom,
                              String recommendationSessionId, Long departureStationId,
                              TravelTime travelTime, List<String> travelStyles) {
        this.memberId = memberId;
        this.resultStationId = resultStationId;
        this.isRandom = isRandom;
        this.recommendationSessionId = recommendationSessionId;
        this.departureStationId = departureStationId;
        this.travelTime = travelTime;
        this.travelStyles = canonicalizeTravelStyles(travelStyles);
    }

    public static String canonicalizeTravelStyles(List<String> travelStyles) {
        if (travelStyles == null || travelStyles.isEmpty()) {
            return null;
        }
        return travelStyles.stream().sorted().collect(Collectors.joining(TRAVEL_STYLE_DELIMITER));
    }
}
