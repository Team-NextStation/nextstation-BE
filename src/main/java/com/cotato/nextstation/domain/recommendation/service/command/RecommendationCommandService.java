package com.cotato.nextstation.domain.recommendation.service.command;

import com.cotato.nextstation.domain.course.repository.CourseRepository;
import com.cotato.nextstation.domain.recommendation.converter.RecommendationConverter;
import com.cotato.nextstation.domain.recommendation.dto.request.CustomRecommendationRequest;
import com.cotato.nextstation.domain.recommendation.dto.request.RandomRecommendationRequest;
import com.cotato.nextstation.domain.recommendation.dto.response.CoursePreviewResponse;
import com.cotato.nextstation.domain.recommendation.dto.response.CustomRecommendationResponse;
import com.cotato.nextstation.domain.recommendation.dto.response.RandomRecommendationResponse;
import com.cotato.nextstation.domain.recommendation.entity.RecommendationLog;
import com.cotato.nextstation.domain.recommendation.enums.TravelTime;
import com.cotato.nextstation.domain.recommendation.exception.RecommendationErrorCode;
import com.cotato.nextstation.domain.recommendation.repository.RecommendationLogRepository;
import com.cotato.nextstation.domain.recommendation.service.port.StationPlaceReader;
import com.cotato.nextstation.domain.recommendation.service.port.StationPlaceView;
import com.cotato.nextstation.domain.recommendation.service.port.StationTagCountReader;
import com.cotato.nextstation.domain.route.repository.StationRouteRepository;
import com.cotato.nextstation.domain.station.entity.Station;
import com.cotato.nextstation.domain.station.exception.StationErrorCode;
import com.cotato.nextstation.domain.station.repository.StationLineRepository;
import com.cotato.nextstation.domain.station.repository.StationLineRepository.StationLineView;
import com.cotato.nextstation.domain.station.repository.StationRepository;
import com.cotato.nextstation.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RecommendationCommandService {

    private static final String COURSE_NAME_SUFFIX = " 환승여행 코스";
    // 코스 미리보기 카테고리 노출 순서(문화공간 → 식당 → 카페 → 산책)
    private static final List<String> CATEGORY_DISPLAY_ORDER = List.of("CULTURE", "FOOD", "CAFE", "WALK");

    // 맞춤추천 점수 = 선택 태그별 장소 수 합 + (충족 태그 수 × TAG_MATCH_WEIGHT)
    private static final int TAG_MATCH_WEIGHT = 10;
    // 가본 역은 후보에서 제외하지 않고 점수만 깎아 순위에 반영한다.
    private static final int VISITED_PENALTY = 4;

    private final StationRepository stationRepository;
    private final StationLineRepository stationLineRepository;
    private final StationRouteRepository stationRouteRepository;
    private final CourseRepository courseRepository;
    private final RecommendationLogRepository recommendationLogRepository;
    private final StationPlaceReader stationPlaceReader;
    private final StationTagCountReader stationTagCountReader;
    private final RecommendationConverter recommendationConverter;

    // 랜덤뽑기. memberId가 있으면 직전 추천 1건을 제외한다.
    public RandomRecommendationResponse drawRandom(Long memberId, RandomRecommendationRequest request) {
        Station picked = pickDrawableStation(request.recommendationSessionId());
        recordRandomLog(memberId, picked.getId(), request.recommendationSessionId());

        // 환승역이면 결과 화면에 소속 노선을 모두 칩으로 노출하므로 대표 노선만이 아니라 전체를 조회한다.
        List<StationLineView> lines = stationLineRepository.findLinesByStationIdIn(List.of(picked.getId()));
        List<StationPlaceView> previewPlaces = selectOnePerCategory(stationPlaceReader.getPlacesByStation(picked.getId()));
        String courseName = picked.getStationName() + COURSE_NAME_SUFFIX;
        return recommendationConverter.toRandomResponse(picked, lines, courseName, previewPlaces);
    }

    // 코스만 다시 뽑기. 역은 고정하고 코스 미리보기만 완전 무작위로 다시 구성한다.
    // 새로 추천된 역이 없으므로 로그를 남기지 않고,
    // 직전 결과 제외 같은 로직도 적용하지 않는다.
    public CoursePreviewResponse redrawCourse(Long stationId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new CustomException(StationErrorCode.STATION_NOT_FOUND));

        // 뽑기 대상이 아닌 역은 장소가 없어 빈 코스가 나간다. 현재 데이터도 장소가 뽑기 역에만 붙어 있지만
        // StationQueryService.getStationPlaces와 같은 이유로 그 전제에 기대지 않고 조건을 명시한다.
        List<StationPlaceView> previewPlaces = station.isDrawable()
                ? selectOnePerCategory(stationPlaceReader.getPlacesByStation(stationId))
                : List.of();
        String courseName = station.getStationName() + COURSE_NAME_SUFFIX;
        return recommendationConverter.toCoursePreview(courseName, previewPlaces);
    }

    /**
     * 맞춤추천. 컷 없이 도달 가능한 역 전체를 순위 매겨 같은 추천 세션과 조건에서 아직 추천하지 않은 역을 순서대로 내려준다.
     * 1. 출발역에서 이동 가능 시간 내 도달 가능한 뽑기 대상 역 전체를 후보로 삼는다(컷 없음).
     * 2. 선택한 여행 스타일 태그 점수(가본 역은 VISITED_PENALTY만큼 감점) 내림차순, 동점은 역 ID 오름차순으로 전체를 정렬한다.
     * 3. 추천 세션과 선택 조건이 같은 이력에서 아직 추천하지 않은 역 중 최상위 1개를 준다.
     * 4. 전부 추천했으면 순위를 버리고 현재 후보 중 직전 추천 1건만 제외한 균등 랜덤으로 전환한다.
     * 5. 비로그인도 같은 세션 순환을 적용하되 가본 역 감점만 생략한다.
     */
    public CustomRecommendationResponse recommendCustom(Long memberId, CustomRecommendationRequest request) {
        validateDepartureStation(request.departureStationId());

        Map<Long, Integer> durationByStationId = findReachableDurations(request.departureStationId(), request.travelTime());
        if (durationByStationId.isEmpty()) {
            throw new CustomException(RecommendationErrorCode.NO_REACHABLE_STATION);
        }

        List<Station> reachableStations = stationRepository.findAllById(durationByStationId.keySet()).stream()
                .filter(Station::isDrawable)
                .toList();
        if (reachableStations.isEmpty()) {
            throw new CustomException(RecommendationErrorCode.NO_REACHABLE_STATION);
        }

        Set<Long> visitedStationIds = memberId == null
                ? Set.of()
                : Set.copyOf(courseRepository.findVisitedStationIds(memberId));
        List<Station> rankedStations = rankStations(reachableStations, request.travelStyles(), visitedStationIds);
        Station picked = pickNextCustomStation(rankedStations, reachableStations, request);
        recordCustomLog(memberId, picked.getId(), request);
        log.info("맞춤추천 완료 - memberId: {}, 출발역: {}, 이동시간: {}, 스타일: {}, 추천역: {}",
                memberId, request.departureStationId(), request.travelTime(), request.travelStyles(), picked.getId());

        List<StationLineView> lines = stationLineRepository.findLinesByStationIdIn(List.of(picked.getId()));
        return recommendationConverter.toCustomResponse(picked, lines, durationByStationId.get(picked.getId()));
    }

    private void validateDepartureStation(Long departureStationId) {
        if (!stationRepository.existsById(departureStationId)) {
            throw new CustomException(RecommendationErrorCode.DEPARTURE_STATION_NOT_FOUND);
        }
    }

    // 출발역에서 도달 가능한 뽑기 대상 역과 소요시간. 이동 시간 제한이 없으면 전 구간을 가져온다.
    private Map<Long, Integer> findReachableDurations(Long departureStationId, TravelTime travelTime) {
        List<StationRouteRepository.ReachableStationView> routes = travelTime.hasLimit()
                ? stationRouteRepository.findReachable(departureStationId, travelTime.getMaxDurationMinutes())
                : stationRouteRepository.findAllFromDeparture(departureStationId);

        Map<Long, Integer> durationByStationId = new HashMap<>();
        for (StationRouteRepository.ReachableStationView route : routes) {
            durationByStationId.put(route.getStationId(), route.getDurationMinutes());
        }
        return durationByStationId;
    }

    // 도달 가능한 역 전체를 점수 내림차순으로 정렬한다(컷 없음). 가본 역은 감점 후 순위를 매기고, 동점은 역 ID 오름차순으로 고정한다.
    private List<Station> rankStations(List<Station> stations, List<String> travelStyles, Set<Long> visitedStationIds) {
        Map<Long, Map<String, Long>> countsByStationId = stationTagCountReader.getPlaceCountsByStationForTags(travelStyles);

        return stations.stream()
                .sorted(Comparator
                        .comparingLong((Station station) -> calculateScore(station, countsByStationId, travelStyles, visitedStationIds))
                        .reversed()
                        .thenComparing(Station::getId))
                .toList();
    }

    // 같은 세션과 조건에서 아직 추천하지 않은 역을 순위대로 준다.
    // 전부 추천했으면 직전 추천 1건만 제외한 균등 랜덤으로 전환한다.
    private Station pickNextCustomStation(List<Station> rankedStations, List<Station> reachableStations,
                                          CustomRecommendationRequest request) {
        String travelStyles = RecommendationLog.canonicalizeTravelStyles(request.travelStyles());
        Set<Long> recommendedStationIds = Set.copyOf(recommendationLogRepository.findCustomRecommendedStationIds(
                request.recommendationSessionId(), request.departureStationId(), request.travelTime(), travelStyles));
        Optional<Station> next = rankedStations.stream()
                .filter(station -> !recommendedStationIds.contains(station.getId()))
                .findFirst();
        if (next.isPresent()) {
            return next.get();
        }
        return pickRandom(excludeLastCustomRecommended(reachableStations, request, travelStyles));
    }

    private List<Station> excludeLastCustomRecommended(List<Station> stations, CustomRecommendationRequest request,
                                                       String travelStyles) {
        Long lastStationId = recommendationLogRepository
                .findTopByRecommendationSessionIdAndIsRandomFalseAndDepartureStationIdAndTravelTimeAndTravelStylesOrderByCreatedAtDescIdDesc(
                        request.recommendationSessionId(), request.departureStationId(), request.travelTime(), travelStyles)
                .map(RecommendationLog::getResultStationId)
                .orElse(null);
        if (lastStationId == null) {
            return stations;
        }

        List<Station> filtered = stations.stream()
                .filter(station -> !station.getId().equals(lastStationId))
                .toList();
        return filtered.isEmpty() ? stations : filtered;
    }

    private long calculateScore(Station station, Map<Long, Map<String, Long>> countsByStationId, List<String> travelStyles,
                                 Set<Long> visitedStationIds) {
        long score = calculateTagScore(countsByStationId.get(station.getId()), travelStyles);
        return visitedStationIds.contains(station.getId()) ? score - VISITED_PENALTY : score;
    }

    private long calculateTagScore(Map<String, Long> countsByTag, List<String> travelStyles) {
        if (countsByTag == null) {
            return 0;
        }

        long placeCountSum = 0;
        int matchedTagCount = 0;
        for (String travelStyle : travelStyles) {
            long count = countsByTag.getOrDefault(travelStyle, 0L);
            placeCountSum += count;
            if (count > 0) {
                matchedTagCount++;
            }
        }
        return placeCountSum + (long) matchedTagCount * TAG_MATCH_WEIGHT;
    }

    private Station pickDrawableStation(String recommendationSessionId) {
        List<Station> drawables = stationRepository.findByIsDrawableTrue();
        if (drawables.isEmpty()) {
            throw new CustomException(RecommendationErrorCode.NO_DRAWABLE_STATION);
        }
        Set<Long> recommendedStationIds = Set.copyOf(
                recommendationLogRepository.findRandomRecommendedStationIds(recommendationSessionId));
        List<Station> remaining = drawables.stream()
                .filter(station -> !recommendedStationIds.contains(station.getId()))
                .toList();
        if (!remaining.isEmpty()) {
            return pickRandom(remaining);
        }
        return pickRandom(excludeLastRandomRecommended(drawables, recommendationSessionId));
    }

    // 로그인 사용자의 직전 추천 1건을 후보에서 제외한다. 제외 후 비면 전체에서 다시 뽑는다.
    // 랜덤뽑기(isRandom=true)와 맞춤추천(isRandom=false)은 서로 다른 화면이라 직전 추천도 각자 독립적으로 조회한다.
    private List<Station> excludeLastRandomRecommended(List<Station> stations, String recommendationSessionId) {
        Long lastStationId = recommendationLogRepository
                .findTopByRecommendationSessionIdAndIsRandomTrueOrderByCreatedAtDescIdDesc(recommendationSessionId)
                .map(RecommendationLog::getResultStationId)
                .orElse(null);
        if (lastStationId == null) {
            return stations;
        }

        List<Station> filtered = stations.stream()
                .filter(station -> !station.getId().equals(lastStationId))
                .toList();
        return filtered.isEmpty() ? stations : filtered;
    }

    private Station pickRandom(List<Station> stations) {
        return stations.get(ThreadLocalRandom.current().nextInt(stations.size()));
    }

    // 랜덤뽑기는 선택 조건이 없어 결과 역만 남긴다.
    private void recordRandomLog(Long memberId, Long stationId, String recommendationSessionId) {
        recommendationLogRepository.save(
                RecommendationLog.builder()
                        .memberId(memberId)
                        .resultStationId(stationId)
                        .isRandom(true)
                        .recommendationSessionId(recommendationSessionId)
                        .build()
        );
    }

    // 맞춤추천은 어떤 조건이 많이 쓰이는지 집계할 수 있도록 선택 조건까지 함께 남긴다.
    private void recordCustomLog(Long memberId, Long stationId, CustomRecommendationRequest request) {
        recommendationLogRepository.save(
                RecommendationLog.builder()
                        .memberId(memberId)
                        .resultStationId(stationId)
                        .isRandom(false)
                        .recommendationSessionId(request.recommendationSessionId())
                        .departureStationId(request.departureStationId())
                        .travelTime(request.travelTime())
                        .travelStyles(request.travelStyles())
                        .build()
        );
    }

    // 카테고리 노출 순서대로 카테고리당 1개씩 선택한다. 장소가 없는 카테고리는 건너뛴다.
    // 같은 역이 다시 뽑혀도 코스가 고정되지 않도록 카테고리 안에서는 무작위로 고른다.
    private List<StationPlaceView> selectOnePerCategory(List<StationPlaceView> places) {
        List<StationPlaceView> selected = new ArrayList<>();
        for (String categoryCode : CATEGORY_DISPLAY_ORDER) {
            List<StationPlaceView> candidates = places.stream()
                    .filter(place -> categoryCode.equals(place.categoryCode()))
                    .toList();
            if (!candidates.isEmpty()) {
                selected.add(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
            }
        }
        return selected;
    }
}
