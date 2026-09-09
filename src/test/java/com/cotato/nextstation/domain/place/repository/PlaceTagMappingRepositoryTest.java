package com.cotato.nextstation.domain.place.repository;

import com.cotato.nextstation.domain.place.entity.Category;
import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.entity.PlaceTag;
import com.cotato.nextstation.domain.place.entity.PlaceTagMapping;
import com.cotato.nextstation.domain.place.enums.CategoryCode;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import com.cotato.nextstation.domain.place.repository.PlaceTagMappingRepository.ApprovedPlaceTagView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase
class PlaceTagMappingRepositoryTest {

    @Autowired
    private PlaceTagMappingRepository placeTagMappingRepository;
    @Autowired
    private PlaceRepository placeRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private PlaceTagRepository placeTagRepository;

    @Test
    @DisplayName("장소 ID로 태그를 조회하면 승인된 장소의 태그만 반환한다")
    void findApprovedTagsByPlaceIdIn_returnsOnlyApprovedPlaceTags() {
        // given
        TestData testData = savePlacesWithAllStatuses();

        // when
        List<ApprovedPlaceTagView> result = placeTagMappingRepository.findApprovedTagsByPlaceIdIn(
                List.of(testData.approvedPlace().getId(), testData.pendingPlace().getId(),
                        testData.rejectedPlace().getId(), testData.deletedPlace().getId()));

        // then
        assertThat(result)
                .extracting(ApprovedPlaceTagView::getPlaceId, ApprovedPlaceTagView::getStationId,
                        ApprovedPlaceTagView::getTagName)
                .containsExactly(tuple(testData.approvedPlace().getId(), 1L, PlaceTagName.NATURE.name()));
    }

    @Test
    @DisplayName("태그명으로 태그를 조회하면 승인된 장소의 태그만 반환한다")
    void findApprovedTagsByTagNameIn_returnsOnlyApprovedPlaceTags() {
        // given
        TestData testData = savePlacesWithAllStatuses();

        // when
        List<ApprovedPlaceTagView> result = placeTagMappingRepository.findApprovedTagsByTagNameIn(
                List.of(PlaceTagName.NATURE.name()));

        // then
        assertThat(result)
                .extracting(ApprovedPlaceTagView::getPlaceId, ApprovedPlaceTagView::getStationId,
                        ApprovedPlaceTagView::getTagName)
                .containsExactly(tuple(testData.approvedPlace().getId(), 1L, PlaceTagName.NATURE.name()));
    }

    private TestData savePlacesWithAllStatuses() {
        Category category = categoryRepository.save(Category.of(CategoryCode.FOOD, "식당", null));
        PlaceTag natureTag = placeTagRepository.save(PlaceTag.of(PlaceTagName.NATURE, true));
        Place approvedPlace = savePlace(category, PlaceStatus.APPROVED, "approved");
        Place pendingPlace = savePlace(category, PlaceStatus.PENDING, "pending");
        Place rejectedPlace = savePlace(category, PlaceStatus.REJECTED, "rejected");
        Place deletedPlace = savePlace(category, PlaceStatus.DELETED, "deleted");

        placeTagMappingRepository.saveAll(List.of(
                PlaceTagMapping.of(approvedPlace, natureTag),
                PlaceTagMapping.of(pendingPlace, natureTag),
                PlaceTagMapping.of(rejectedPlace, natureTag),
                PlaceTagMapping.of(deletedPlace, natureTag)));
        return new TestData(approvedPlace, pendingPlace, rejectedPlace, deletedPlace);
    }

    private Place savePlace(Category category, PlaceStatus status, String kakaoPlaceId) {
        Place place = Place.builder()
                .stationId(1L)
                .category(category)
                .description("설명")
                .placeName(kakaoPlaceId + " 장소")
                .address("서울시")
                .contactNumber(null)
                .xCoordinate(127.0)
                .yCoordinate(37.0)
                .kakaoPlaceId(kakaoPlaceId)
                .status(status)
                .build();
        ReflectionTestUtils.setField(place, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(place, "updatedAt", LocalDateTime.now());
        return placeRepository.save(place);
    }

    private record TestData(Place approvedPlace, Place pendingPlace, Place rejectedPlace, Place deletedPlace) {
    }
}
