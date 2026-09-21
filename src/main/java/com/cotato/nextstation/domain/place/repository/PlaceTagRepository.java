package com.cotato.nextstation.domain.place.repository;

import com.cotato.nextstation.domain.place.entity.PlaceTag;
import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaceTagRepository extends JpaRepository<PlaceTag, Long> {

    Optional<PlaceTag> findByName(PlaceTagName name);

    Optional<PlaceTag> findByNameAndIsActiveTrue(PlaceTagName name);

    List<PlaceTag> findAllByIsActiveTrueOrderByIdAsc();

    // 폐지된 place_tag 정리용 - enum에서 제거된 태그명도 지울 수 있게 문자열 기준으로 삭제한다
    @Modifying
    @Query(value = "DELETE FROM place_tag WHERE name IN (:tagNames)", nativeQuery = true)
    void deleteByNames(@Param("tagNames") List<String> tagNames);
}
