package com.cotato.nextstation.domain.place.entity;

import com.cotato.nextstation.domain.place.enums.ImageSourceType;
import com.cotato.nextstation.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소 상세 화면에 노출되는 대표 이미지 목록.
 *
 * sourceType으로 실제 장소 촬영본(PLACE)인지, 리뷰에서 가져온 이미지(REVIEW)인지 구분.
 *  sortOrder: 대표 이미지 노출 순서, 0이 대표(썸네일) 이미지.
 */
@Entity
@Table(name = "place_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceImage extends BaseTimeEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_review_id")
    private PlaceReview placeReview;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private ImageSourceType sourceType;

    /**
     * 사진의 출처 (공공누리 1유형, CC BY, 비짓서울, 직접 촬영 등).
     * 공공누리 제1유형 등은 출처표시가 이용 조건이므로 이 값으로 출처 목록을 만들 수 있어야 한다.
     */
    @Column(name = "source")
    private String source;

    @Builder
    public PlaceImage(Place place, PlaceReview placeReview, String imageUrl,
                      int sortOrder, ImageSourceType sourceType, String source) {
        this.place = place;
        this.placeReview = placeReview;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.sourceType = sourceType;
        this.source = source;
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
