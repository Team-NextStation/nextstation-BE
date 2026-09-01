package com.cotato.nextstation.domain.place.repository;

import com.cotato.nextstation.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place>  findByStationId(Long stationId);

    // Place의 @SQLRestriction은 네이티브 쿼리에 적용되지 않는다.
    // 관리자 목록은 APPROVED 외 상태도 읽어야 하므로 place를 직접 조회해 제약을 우회한다.
    @Query(value = "SELECT * FROM place WHERE status = :status", nativeQuery = true)
    List<Place> findAllByStatusForAdmin(@Param("status") String status);
}
