package com.cotato.nextstation.domain.place.repository;

import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.entity.PlaceImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PlaceImageRepository extends JpaRepository<PlaceImage, Long> {

    // 장소 상세 조회 - 대표 이미지 노출 순서대로
    List<PlaceImage> findByPlaceOrderBySortOrderAsc(Place place);

    List<PlaceImage> findByPlaceIdIn(List<Long> placeIds);

    @Query(value = """
            SELECT pi.place_id AS placeId, pi.image_url AS imageUrl
            FROM place_image pi
            WHERE pi.place_id IN (:placeIds)
            ORDER BY pi.place_id, pi.sort_order, pi.id
            """, nativeQuery = true)
    List<AdminPlaceImageView> findAdminImages(@Param("placeIds") List<Long> placeIds);

    interface AdminPlaceImageView {
        Long getPlaceId();
        String getImageUrl();
    }

}
