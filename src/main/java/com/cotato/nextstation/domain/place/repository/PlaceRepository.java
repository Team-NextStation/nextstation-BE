package com.cotato.nextstation.domain.place.repository;

import com.cotato.nextstation.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByStationId(Long stationId);

    default boolean existsByStationAndKakaoPlaceIdForAdmin(Long stationId, String kakaoPlaceId) {
        return countByStationAndKakaoPlaceIdForAdmin(stationId, kakaoPlaceId) > 0;
    }

    @Query(value = "SELECT COUNT(1) FROM place WHERE station_id = :stationId AND kakao_place_id = :kakaoPlaceId",
            nativeQuery = true)
    long countByStationAndKakaoPlaceIdForAdmin(@Param("stationId") Long stationId,
                                               @Param("kakaoPlaceId") String kakaoPlaceId);
}
