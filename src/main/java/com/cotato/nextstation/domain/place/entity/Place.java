package com.cotato.nextstation.domain.place.entity;

import com.cotato.nextstation.domain.place.enums.PlaceStatus;
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
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "place")
@SQLRestriction("status = 'APPROVED'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseTimeEntity {

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String description;

    @Column(name = "place_name", nullable = false)
    private String placeName;

    @Column(nullable = false)
    private String address;

    @Column(name = "contact_number")
    private String contactNumber;

    @Column(name = "x_coordinate", nullable = false)
    private Double xCoordinate;

    @Column(name = "y_coordinate", nullable = false)
    private Double yCoordinate;

    @Column(name = "kakao_place_url")
    private String kakaoPlaceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceStatus status;

    @Column(name = "delete_reason", length = 255)
    private String deleteReason;

    @Builder
    public Place(Long stationId,  Category category, String description, String placeName, String address,
                 String contactNumber, Double xCoordinate, Double yCoordinate, String kakaoPlaceUrl) {
        this.stationId = stationId;
        this.category = category;
        this.description = description;
        this.placeName = placeName;
        this.address = address;
        this.contactNumber = contactNumber;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.kakaoPlaceUrl = kakaoPlaceUrl;
        this.status = PlaceStatus.APPROVED;
    }
}