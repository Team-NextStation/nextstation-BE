package com.cotato.nextstation.domain.place.repository;

import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.entity.PlaceTagMapping;
import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PlaceTagMappingRepository extends JpaRepository<PlaceTagMapping, Long> {

    // 장소 상세 조회 - 이 장소의 태그 전부 조회 (표시용)
    @EntityGraph(attributePaths = {"placeTag"})
    List<PlaceTagMapping> findByPlace(Place place);

    // 폐지된 place_tag 정리용 - enum에서 제거된 태그명도 지울 수 있게 문자열 기준으로 삭제한다
    @Modifying
    @Query(value = "DELETE ptm FROM place_tag_mapping ptm JOIN place_tag pt ON pt.id = ptm.place_tag_id WHERE pt.name IN (:tagNames)", nativeQuery = true)
    void deleteByPlaceTagNames(@Param("tagNames") List<String> tagNames);

    // Course 조회 전용 - placeIds 여러 개의 태그를 한 번에 조회
    @EntityGraph(attributePaths = {"place", "placeTag"})
    List<PlaceTagMapping> findByPlaceIdIn(List<Long> placeIds);

    @EntityGraph(attributePaths = {"place", "placeTag"})
    List<PlaceTagMapping> findByPlaceTagNameIn(List<PlaceTagName> tagNames);

    @Query(value = """
            SELECT ptm.place_id AS placeId, pt.name AS tagName
            FROM place_tag_mapping ptm
            JOIN place_tag pt ON pt.id = ptm.place_tag_id
            WHERE ptm.place_id IN (:placeIds)
            ORDER BY ptm.place_id, ptm.id
            """, nativeQuery = true)
    List<AdminPlaceTagView> findAdminTags(@Param("placeIds") List<Long> placeIds);

    interface AdminPlaceTagView {
        Long getPlaceId();
        String getTagName();
    }
}
