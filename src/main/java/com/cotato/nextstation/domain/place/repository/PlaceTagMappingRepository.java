package com.cotato.nextstation.domain.place.repository;

import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.entity.PlaceTagMapping;
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

    /**
     * 사용자 노출용 태그 조회. Place의 @SQLRestriction에 기대지 않고 승인 장소를 SQL에서 명시한다.
     * 엔티티 연관관계를 초기화하지 않아 비승인 장소 매핑의 null 연관관계가 섞이지 않는다.
     */
    @Query(value = """
            SELECT ptm.place_id AS placeId, p.station_id AS stationId, pt.name AS tagName
            FROM place_tag_mapping ptm
            JOIN place p ON p.id = ptm.place_id AND p.status = 'APPROVED'
            JOIN place_tag pt ON pt.id = ptm.place_tag_id
            WHERE ptm.place_id IN (:placeIds)
            """, nativeQuery = true)
    List<ApprovedPlaceTagView> findApprovedTagsByPlaceIdIn(@Param("placeIds") List<Long> placeIds);

    @Query(value = """
            SELECT ptm.place_id AS placeId, p.station_id AS stationId, pt.name AS tagName
            FROM place_tag_mapping ptm
            JOIN place p ON p.id = ptm.place_id AND p.status = 'APPROVED'
            JOIN place_tag pt ON pt.id = ptm.place_tag_id
            WHERE pt.name IN (:tagNames)
            """, nativeQuery = true)
    List<ApprovedPlaceTagView> findApprovedTagsByTagNameIn(@Param("tagNames") List<String> tagNames);

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

    interface ApprovedPlaceTagView {
        Long getPlaceId();
        Long getStationId();
        String getTagName();
    }
}
