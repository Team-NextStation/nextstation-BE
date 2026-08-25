package com.cotato.nextstation.domain.recommendation.repository;

import com.cotato.nextstation.domain.recommendation.entity.RecommendationLog;
import com.cotato.nextstation.domain.recommendation.enums.TravelTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {

    // 랜덤뽑기의 직전 추천 1건. 로그인 사용자에게만 조회한다.
    // created_at이 같은 순간에 겹칠 때를 대비해 id로 tie-break 한다.
    Optional<RecommendationLog> findTopByMemberIdAndIsRandomOrderByCreatedAtDescIdDesc(Long memberId, boolean isRandom);

    // 같은 추천 세션과 선택 조건에서 이미 제공한 역을 조회한다.
    @Query("SELECT DISTINCT rl.resultStationId FROM RecommendationLog rl " +
            "WHERE rl.recommendationSessionId = :sessionId AND rl.isRandom = false " +
            "AND rl.departureStationId = :departureStationId AND rl.travelTime = :travelTime " +
            "AND rl.travelStyles = :travelStyles")
    List<Long> findCustomRecommendedStationIds(@Param("sessionId") String sessionId,
                                               @Param("departureStationId") Long departureStationId,
                                               @Param("travelTime") TravelTime travelTime,
                                               @Param("travelStyles") String travelStyles);

    Optional<RecommendationLog> findTopByRecommendationSessionIdAndIsRandomFalseAndDepartureStationIdAndTravelTimeAndTravelStylesOrderByCreatedAtDescIdDesc(
            String recommendationSessionId, Long departureStationId, TravelTime travelTime, String travelStyles);
}
