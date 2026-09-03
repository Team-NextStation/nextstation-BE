package com.cotato.nextstation.domain.place.repository;

import com.cotato.nextstation.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByStationId(Long stationId);
}
