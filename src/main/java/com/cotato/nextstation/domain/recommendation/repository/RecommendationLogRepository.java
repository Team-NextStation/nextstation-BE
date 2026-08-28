package com.cotato.nextstation.domain.recommendation.repository;

import com.cotato.nextstation.domain.recommendation.entity.RecommendationLog;
import com.cotato.nextstation.domain.recommendation.enums.TravelTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {

    // 랜덤뽑기의 직전 추천 1건. 로그인 사용자에게만 조회한다.
    // created_at이 같은 순간에 겹칠 때를 대비해 id로 tie-break 한다.
    @Query("SELECT DISTINCT rl.resultStationId FROM RecommendationLog rl " +
            "WHERE rl.recommendationSessionId = :sessionId AND rl.isRandom = true")
    List<Long> findRandomRecommendedStationIds(@Param("sessionId") String sessionId);

    Optional<RecommendationLog> findTopByRecommendationSessionIdAndIsRandomTrueOrderByCreatedAtDescIdDesc(
            String recommendationSessionId);

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
    Optional<RecommendationLog> findTopByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);

    // 리포트 집계 구간은 모두 [from, to), 일간·주간 리포트가 같은 건을 중복 집계하지 않도록 끝을 배제한다.
    @Query("SELECT COUNT(l) FROM RecommendationLog l "
            + "WHERE l.isRandom = :isRandom AND l.createdAt >= :from AND l.createdAt < :to")
    long countByIsRandomInPeriod(@Param("isRandom") boolean isRandom,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    // RecommendationLog는 Station과 연관관계 없이 FK 식별자만 들고 있어 명시적 조인으로 역명을 가져온다.
    // 건수가 같을 때 순위가 실행마다 뒤집히지 않도록 역명으로 tie-break 한다.
    @Query("SELECT s.stationName AS name, COUNT(l.id) AS count "
            + "FROM RecommendationLog l JOIN Station s ON s.id = l.resultStationId "
            + "WHERE l.createdAt >= :from AND l.createdAt < :to "
            + "GROUP BY s.stationName "
            + "ORDER BY COUNT(l.id) DESC, s.stationName ASC")
    List<NameCountView> findTopResultStations(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to,
                                              Pageable pageable);

    // departure_station_id는 맞춤추천에만 채워지므로 조인 자체가 랜덤뽑기를 걸러낸다.
    @Query("SELECT s.stationName AS name, COUNT(l.id) AS count "
            + "FROM RecommendationLog l JOIN Station s ON s.id = l.departureStationId "
            + "WHERE l.createdAt >= :from AND l.createdAt < :to "
            + "GROUP BY s.stationName "
            + "ORDER BY COUNT(l.id) DESC, s.stationName ASC")
    List<NameCountView> findTopDepartureStations(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to,
                                                 Pageable pageable);

    @Query("SELECT l.travelTime AS travelTime, COUNT(l.id) AS count "
            + "FROM RecommendationLog l "
            + "WHERE l.travelTime IS NOT NULL AND l.createdAt >= :from AND l.createdAt < :to "
            + "GROUP BY l.travelTime")
    List<TravelTimeCountView> countByTravelTimeInPeriod(@Param("from") LocalDateTime from,
                                                        @Param("to") LocalDateTime to);

    // 태그는 콤마로 join된 한 컬럼이라 GROUP BY 불가능
    @Query("SELECT l.travelStyles FROM RecommendationLog l "
            + "WHERE l.travelStyles IS NOT NULL AND l.createdAt >= :from AND l.createdAt < :to")
    List<String> findTravelStylesInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    interface NameCountView {
        String getName();
        long getCount();
    }

    interface TravelTimeCountView {
        TravelTime getTravelTime();
        long getCount();
    }
}
