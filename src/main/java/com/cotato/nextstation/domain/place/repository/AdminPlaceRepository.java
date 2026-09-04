package com.cotato.nextstation.domain.place.repository;

import com.cotato.nextstation.domain.place.entity.Place;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link Place}의 SQLRestriction을 우회해 모든 등록 상태를 조회하는 관리자 전용 저장소이다.
 */
public interface AdminPlaceRepository extends Repository<Place, Long> {

    /**
     * 컬렉션인 태그와 이미지는 이 쿼리에 조인하지 않고 페이지에 포함된 placeId로 일괄 조회한다.
     */
    @Query(value = """
            SELECT p.id AS placeId,
                   p.place_name AS placeName,
                   p.description AS description,
                   p.status AS status,
                   s.id AS stationId,
                   s.station_name AS stationName,
                   l.id AS lineId,
                   l.name AS lineName,
                   l.code AS lineCode,
                   c.code AS categoryCode,
                   c.name AS categoryName
            FROM place p
            JOIN station s ON s.id = p.station_id
            LEFT JOIN line l ON l.id = s.draw_line_id
            JOIN category c ON c.id = p.category_id
            WHERE (:lineId IS NULL OR l.id = :lineId)
              AND (:stationId IS NULL OR s.id = :stationId)
              AND (:categoryCode IS NULL OR c.code = :categoryCode)
              AND (:status IS NULL OR p.status = :status)
              AND (:cursorPlaceName IS NULL
                   OR p.place_name > :cursorPlaceName
                   OR (p.place_name = :cursorPlaceName AND p.id > :cursorPlaceId))
            ORDER BY p.place_name ASC, p.id ASC
            """, nativeQuery = true)
    List<AdminPlaceView> findAdminPlaces(@Param("lineId") Long lineId,
                                         @Param("stationId") Long stationId,
                                         @Param("categoryCode") String categoryCode,
                                         @Param("status") String status,
                                         @Param("cursorPlaceName") String cursorPlaceName,
                                         @Param("cursorPlaceId") Long cursorPlaceId,
                                         Pageable pageable);

    @Query(value = """
            SELECT p.id AS placeId,
                   p.place_name AS placeName,
                   p.description AS description,
                   p.status AS status,
                   s.id AS stationId,
                   s.station_name AS stationName,
                   l.id AS lineId,
                   l.name AS lineName,
                   l.code AS lineCode,
                   c.code AS categoryCode,
                   c.name AS categoryName
            FROM place p
            JOIN station s ON s.id = p.station_id
            LEFT JOIN line l ON l.id = s.draw_line_id
            JOIN category c ON c.id = p.category_id
            WHERE p.place_name LIKE CONCAT('%', :pattern, '%') ESCAPE '!'
            ORDER BY
                CASE
                    WHEN p.place_name = :keyword THEN 0
                    WHEN p.place_name LIKE CONCAT(:pattern, '%') ESCAPE '!' THEN 1
                    ELSE 2
                END,
                CHAR_LENGTH(p.place_name),
                p.place_name ASC,
                p.id ASC
            """, nativeQuery = true)
    List<AdminPlaceView> searchAdminPlaces(@Param("keyword") String keyword,
                                           @Param("pattern") String pattern,
                                           Pageable pageable);

    @Query(value = """
            SELECT p.id AS placeId,
                   p.place_name AS placeName,
                   p.description AS description,
                   p.status AS status,
                   p.delete_reason AS deleteReason,
                   p.reject_reason AS rejectReason,
                   p.address AS address,
                   p.x_coordinate AS xCoordinate,
                   p.y_coordinate AS yCoordinate,
                   p.kakao_place_url AS kakaoPlaceUrl,
                   s.id AS stationId,
                   s.station_name AS stationName,
                   l.id AS lineId,
                   l.name AS lineName,
                   l.code AS lineCode,
                   c.code AS categoryCode,
                   c.name AS categoryName
            FROM place p
            JOIN station s ON s.id = p.station_id
            LEFT JOIN line l ON l.id = s.draw_line_id
            JOIN category c ON c.id = p.category_id
            WHERE p.id = :placeId
            """, nativeQuery = true)
    Optional<AdminPlaceDetailView> findAdminPlaceDetail(@Param("placeId") Long placeId);

    @Query(value = """
            SELECT DISTINCT l.id AS lineId, l.name AS lineName, l.code AS lineCode
            FROM place p
            JOIN station s ON s.id = p.station_id
            JOIN line l ON l.id = s.draw_line_id
            ORDER BY l.id ASC
            """, nativeQuery = true)
    List<AdminLineView> findAdminAvailableLines();

    @Query(value = """
            SELECT DISTINCT s.id AS stationId, s.station_name AS stationName
            FROM place p
            JOIN station s ON s.id = p.station_id
            WHERE s.draw_line_id = :lineId
            ORDER BY s.station_name ASC
            """, nativeQuery = true)
    List<AdminStationView> findAdminAvailableStations(@Param("lineId") Long lineId);

    interface AdminPlaceView {
        Long getPlaceId();
        String getPlaceName();
        String getDescription();
        String getStatus();
        Long getStationId();
        String getStationName();
        Long getLineId();
        String getLineName();
        String getLineCode();
        String getCategoryCode();
        String getCategoryName();
    }

    interface AdminPlaceDetailView extends AdminPlaceView {
        String getAddress();
        Double getXCoordinate();
        Double getYCoordinate();
        String getKakaoPlaceUrl();
        String getDeleteReason();
        String getRejectReason();
    }

    interface AdminLineView {
        Long getLineId();
        String getLineName();
        String getLineCode();
    }

    interface AdminStationView {
        Long getStationId();
        String getStationName();
    }
}
