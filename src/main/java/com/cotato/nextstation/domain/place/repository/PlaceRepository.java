package com.cotato.nextstation.domain.place.repository;

import com.cotato.nextstation.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByStationId(Long stationId);

    /**
     * 이미 코스·여행일지에 저장된 장소를 복원하는 전용 조회다.
     * Place 엔티티의 @SQLRestriction을 적용하지 않아 비승인 장소도 상태와 함께 읽는다.
     */
    @Query(value = """
            SELECT p.id AS placeId,
                   p.place_name AS placeName,
                   p.description AS description,
                   c.code AS categoryCode,
                   c.name AS categoryName,
                   COALESCE((SELECT pi.image_url
                             FROM place_image pi
                             WHERE pi.place_id = p.id
                             ORDER BY pi.sort_order, pi.id
                             LIMIT 1), c.default_image_url) AS imageUrl,
                   p.x_coordinate AS xCoordinate,
                   p.y_coordinate AS yCoordinate,
                   p.status AS placeStatus
            FROM place p
            JOIN category c ON c.id = p.category_id
            WHERE p.id IN (:placeIds)
            """, nativeQuery = true)
    List<HistoricalPlaceView> findHistoricalPlacesByIdIn(@Param("placeIds") List<Long> placeIds);

    default boolean existsByStationAndKakaoPlaceIdForAdmin(Long stationId, String kakaoPlaceId) {
        return countByStationAndKakaoPlaceIdForAdmin(stationId, kakaoPlaceId) > 0;
    }

    @Query(value = "SELECT COUNT(1) FROM place WHERE station_id = :stationId AND kakao_place_id = :kakaoPlaceId",
            nativeQuery = true)
    long countByStationAndKakaoPlaceIdForAdmin(@Param("stationId") Long stationId,
                                               @Param("kakaoPlaceId") String kakaoPlaceId);

    interface HistoricalPlaceView {
        Long getPlaceId();
        String getPlaceName();
        String getDescription();
        String getCategoryCode();
        String getCategoryName();
        String getImageUrl();
        Double getXCoordinate();
        Double getYCoordinate();
        String getPlaceStatus();
    }
}
