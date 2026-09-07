package com.cotato.nextstation.domain.place.service.command;

import com.cotato.nextstation.domain.image.service.command.ImageCommandService;
import com.cotato.nextstation.domain.member.service.query.AdminGuard;
import com.cotato.nextstation.domain.place.dto.request.AdminPlaceCreateRequest;
import com.cotato.nextstation.domain.place.dto.request.AdminPlaceUpdateRequest;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCreateResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceDetailResponse;
import com.cotato.nextstation.domain.place.entity.Category;
import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.entity.PlaceImage;
import com.cotato.nextstation.domain.place.entity.PlaceTag;
import com.cotato.nextstation.domain.place.enums.CategoryCode;
import com.cotato.nextstation.domain.place.enums.ImageSourceType;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import com.cotato.nextstation.domain.place.exception.PlaceErrorCode;
import com.cotato.nextstation.domain.place.repository.CategoryRepository;
import com.cotato.nextstation.domain.place.repository.AdminPlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceTagMappingRepository;
import com.cotato.nextstation.domain.place.repository.PlaceTagRepository;
import com.cotato.nextstation.domain.place.service.query.AdminPlaceQueryService;
import com.cotato.nextstation.domain.station.exception.StationErrorCode;
import com.cotato.nextstation.domain.station.repository.StationRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class AdminPlaceCommandServiceTest {

    @InjectMocks
    private AdminPlaceCommandService adminPlaceCommandService;

    @Mock
    private AdminGuard adminGuard;

    @Mock
    private StationRepository stationRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private PlaceTagRepository placeTagRepository;

    @Mock
    private AdminPlaceRepository adminPlaceRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceTagMappingRepository placeTagMappingRepository;

    @Mock
    private PlaceImageRepository placeImageRepository;

    @Mock
    private ImageCommandService imageCommandService;

    @Mock
    private AdminPlaceQueryService adminPlaceQueryService;

    private static final Long ADMIN_ID = 1L;
    private static final Long STATION_ID = 12L;
    private static final Long PLACE_ID = 301L;
    private static final String KAKAO_PLACE_ID = "8137464";
    private static final String IMAGE_URL_PREFIX =
            "https://test-bucket.s3.ap-northeast-2.amazonaws.com/images/static/places/8137464/";

    private AdminPlaceCreateRequest request(List<PlaceTagName> tagNames, List<String> imageUrls) {
        return new AdminPlaceCreateRequest(
                STATION_ID, CategoryCode.CAFE, "역에서 5분 거리의 통유리 카페", tagNames, imageUrls,
                KAKAO_PLACE_ID, "스타벅스 강남역점", "서울 강남구 강남대로 390", "02-1234-5678",
                127.0276, 37.4979);
    }

    // 저장 시 id가 채워진 Place를 돌려주도록 흉내낸다
    private void givenPlaceSaved() {
        given(placeRepository.saveAndFlush(any(Place.class))).willAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            ReflectionTestUtils.setField(place, "id", PLACE_ID);
            return place;
        });
    }

    private void givenStationAndCategoryExist() {
        given(stationRepository.existsById(STATION_ID)).willReturn(true);
        given(categoryRepository.findByCode(CategoryCode.CAFE))
                .willReturn(Optional.of(Category.of(CategoryCode.CAFE, "카페", "https://cdn/default-cafe.jpg")));
    }

    private Place place(PlaceStatus status) {
        Place place = Place.builder()
                .stationId(STATION_ID)
                .category(Category.of(CategoryCode.CAFE, "카페", "default.jpg"))
                .description("기존 설명")
                .placeName("기존 장소")
                .address("서울시")
                .xCoordinate(127.0)
                .yCoordinate(37.0)
                .kakaoPlaceId(KAKAO_PLACE_ID)
                .status(status)
                .build();
        ReflectionTestUtils.setField(place, "id", PLACE_ID);
        return place;
    }

    private PlaceImage image(Place place, long id, String name, int sortOrder) {
        PlaceImage image = PlaceImage.builder()
                .place(place)
                .imageUrl(IMAGE_URL_PREFIX + name)
                .sortOrder(sortOrder)
                .sourceType(ImageSourceType.PLACE)
                .build();
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }

    @Test
    @DisplayName("등록된 장소는 PENDING 상태로 저장된다")
    void createPlace_savedAsPending() {
        givenStationAndCategoryExist();
        givenPlaceSaved();

        AdminPlaceCreateResponse response =
                adminPlaceCommandService.createPlace(ADMIN_ID, request(List.of(), List.of()));

        ArgumentCaptor<Place> captor = ArgumentCaptor.forClass(Place.class);
        then(placeRepository).should().saveAndFlush(captor.capture());
        Place saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(PlaceStatus.PENDING);
        assertThat(saved.getStationId()).isEqualTo(STATION_ID);
        assertThat(saved.getKakaoPlaceId()).isEqualTo(KAKAO_PLACE_ID);
        assertThat(saved.getXCoordinate()).isEqualTo(127.0276);
        assertThat(response.placeId()).isEqualTo(PLACE_ID);
        assertThat(response.status()).isEqualTo(PlaceStatus.PENDING);
    }

    @Test
    @DisplayName("선택한 태그가 매핑까지 저장되고, 같은 태그를 두 번 보내면 한 번만 저장된다")
    void createPlace_savesTagMappings() {
        givenStationAndCategoryExist();
        givenPlaceSaved();
        given(placeTagRepository.findByName(PlaceTagName.HOTPLACE))
                .willReturn(Optional.of(PlaceTag.of(PlaceTagName.HOTPLACE, true)));
        given(placeTagRepository.findByName(PlaceTagName.PHOTO_SPOT))
                .willReturn(Optional.of(PlaceTag.of(PlaceTagName.PHOTO_SPOT, true)));

        adminPlaceCommandService.createPlace(ADMIN_ID,
                request(List.of(PlaceTagName.HOTPLACE, PlaceTagName.PHOTO_SPOT, PlaceTagName.HOTPLACE), List.of()));

        then(placeTagMappingRepository).should(times(2)).save(any());
    }

    @Test
    @DisplayName("사진은 받은 순서대로 저장되고 첫 번째가 대표 이미지가 된다")
    void createPlace_savesImagesInOrder() {
        givenStationAndCategoryExist();
        givenPlaceSaved();

        adminPlaceCommandService.createPlace(ADMIN_ID,
                request(List.of(), List.of(IMAGE_URL_PREFIX + "a.jpg", IMAGE_URL_PREFIX + "b.jpg")));

        ArgumentCaptor<PlaceImage> captor = ArgumentCaptor.forClass(PlaceImage.class);
        then(placeImageRepository).should(times(2)).save(captor.capture());

        List<PlaceImage> images = captor.getAllValues();
        assertThat(images.get(0).getSortOrder()).isZero();
        assertThat(images.get(0).getImageUrl()).endsWith("a.jpg");
        assertThat(images.get(1).getSortOrder()).isEqualTo(1);
        assertThat(images).allSatisfy(image -> assertThat(image.getSourceType()).isEqualTo(ImageSourceType.PLACE));
    }

    @Test
    @DisplayName("사진 없이도 장소 등록은 성공한다")
    void createPlace_withoutImages() {
        givenStationAndCategoryExist();
        givenPlaceSaved();

        adminPlaceCommandService.createPlace(ADMIN_ID, request(List.of(), null));

        then(placeImageRepository).should(never()).save(any());
        then(imageCommandService).should(never()).validatePlaceImageUrl(anyString(), anyString());
    }

    @Test
    @DisplayName("장소 사진 경로가 아닌 이미지 URL이면 장소를 저장하지 않는다")
    void createPlace_invalidImageUrl() {
        givenStationAndCategoryExist();
        willThrow(new CustomException(GlobalErrorCode.INVALID_REQUEST))
                .given(imageCommandService).validatePlaceImageUrl("https://evil.example.org/a.jpg", KAKAO_PLACE_ID);

        assertThatThrownBy(() -> adminPlaceCommandService.createPlace(ADMIN_ID,
                request(List.of(), List.of("https://evil.example.org/a.jpg"))))
                .isInstanceOf(CustomException.class);

        then(placeRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("존재하지 않는 역이면 404다")
    void createPlace_stationNotFound() {
        given(stationRepository.existsById(STATION_ID)).willReturn(false);

        assertThatThrownBy(() -> adminPlaceCommandService.createPlace(ADMIN_ID, request(List.of(), List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", StationErrorCode.STATION_NOT_FOUND);

        then(placeRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("같은 역에 이미 등록된 카카오 장소면 409다")
    void createPlace_duplicate() {
        given(stationRepository.existsById(STATION_ID)).willReturn(true);
        given(placeRepository.existsByStationAndKakaoPlaceIdForAdmin(STATION_ID, KAKAO_PLACE_ID)).willReturn(true);

        assertThatThrownBy(() -> adminPlaceCommandService.createPlace(ADMIN_ID, request(List.of(), List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.PLACE_ALREADY_REGISTERED);

        then(placeRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("중복 확인을 통과해도 유니크 제약에 걸리면 409로 바꾼다")
    void createPlace_duplicateOnSave() {
        givenStationAndCategoryExist();
        given(placeRepository.saveAndFlush(any(Place.class)))
                .willThrow(new DataIntegrityViolationException("uk_place_station_kakao"));

        assertThatThrownBy(() -> adminPlaceCommandService.createPlace(ADMIN_ID, request(List.of(), List.of())))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.PLACE_ALREADY_REGISTERED);

        then(placeTagMappingRepository).shouldHaveNoInteractions();
        then(placeImageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("관리자가 아니면 아무것도 저장하지 않고 막는다")
    void createPlace_notAdmin() {
        willThrow(new CustomException(GlobalErrorCode.FORBIDDEN)).given(adminGuard).requireAdmin(ADMIN_ID);

        assertThatThrownBy(() -> adminPlaceCommandService.createPlace(ADMIN_ID, request(List.of(), List.of())))
                .isInstanceOf(CustomException.class);

        then(stationRepository).shouldHaveNoInteractions();
        then(placeRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("설명만 보내면 다른 수정 저장소를 건드리지 않고 설명만 변경한다")
    void updatePlace_descriptionOnly() {
        Place place = place(PlaceStatus.APPROVED);
        AdminPlaceDetailResponse expected = org.mockito.Mockito.mock(AdminPlaceDetailResponse.class);
        given(adminPlaceRepository.findAdminPlaceForUpdate(PLACE_ID)).willReturn(Optional.of(place));
        given(adminPlaceQueryService.getPlaceDetail(ADMIN_ID, PLACE_ID)).willReturn(expected);

        AdminPlaceDetailResponse response = adminPlaceCommandService.updatePlace(
                ADMIN_ID, PLACE_ID, new AdminPlaceUpdateRequest(null, "새 설명", null, null));

        assertThat(response).isSameAs(expected);
        assertThat(place.getDescription()).isEqualTo("새 설명");
        then(placeTagMappingRepository).should(never()).deleteAdminMappingsByPlaceId(any());
        then(placeImageRepository).should(never()).findAdminImagesByPlaceId(any());
    }

    @Test
    @DisplayName("태그 교체와 사진 삭제·추가를 함께 반영하고 사진 순서를 0부터 다시 매긴다")
    @SuppressWarnings("unchecked")
    void updatePlace_updatesTagsAndImagesAndNormalizesOrder() {
        Place place = place(PlaceStatus.PENDING);
        PlaceImage first = image(place, 11L, "a.jpg", 0);
        PlaceImage second = image(place, 12L, "b.jpg", 1);
        PlaceImage third = image(place, 13L, "c.jpg", 2);
        PlaceTag hotplace = PlaceTag.of(PlaceTagName.HOTPLACE, true);
        PlaceTag indoor = PlaceTag.of(PlaceTagName.INDOOR, true);
        String newImageUrl = IMAGE_URL_PREFIX + "new.jpg";
        AdminPlaceDetailResponse expected = org.mockito.Mockito.mock(AdminPlaceDetailResponse.class);

        given(adminPlaceRepository.findAdminPlaceForUpdate(PLACE_ID)).willReturn(Optional.of(place));
        given(placeImageRepository.findAdminImagesByPlaceId(PLACE_ID))
                .willReturn(List.of(first, second, third));
        given(placeTagRepository.findByNameAndIsActiveTrue(PlaceTagName.HOTPLACE))
                .willReturn(Optional.of(hotplace));
        given(placeTagRepository.findByNameAndIsActiveTrue(PlaceTagName.INDOOR))
                .willReturn(Optional.of(indoor));
        given(adminPlaceQueryService.getPlaceDetail(ADMIN_ID, PLACE_ID)).willReturn(expected);

        adminPlaceCommandService.updatePlace(ADMIN_ID, PLACE_ID,
                new AdminPlaceUpdateRequest(
                        List.of(PlaceTagName.HOTPLACE, PlaceTagName.INDOOR), null,
                        List.of(newImageUrl), List.of(11L, 12L)));

        then(placeTagMappingRepository).should().deleteAdminMappingsByPlaceId(PLACE_ID);
        then(placeTagMappingRepository).should(times(2)).save(any());
        ArgumentCaptor<Iterable<PlaceImage>> deletedImagesCaptor = ArgumentCaptor.forClass(Iterable.class);
        then(placeImageRepository).should().deleteAll(deletedImagesCaptor.capture());
        assertThat(StreamSupport.stream(deletedImagesCaptor.getValue().spliterator(), false).toList())
                .containsExactlyInAnyOrder(first, second);

        ArgumentCaptor<Iterable<PlaceImage>> imagesCaptor = ArgumentCaptor.forClass(Iterable.class);
        then(placeImageRepository).should().saveAll(imagesCaptor.capture());
        List<PlaceImage> finalImages = StreamSupport.stream(imagesCaptor.getValue().spliterator(), false).toList();
        assertThat(finalImages).hasSize(2);
        assertThat(finalImages.get(0)).isSameAs(third);
        assertThat(finalImages.get(0).getSortOrder()).isZero();
        assertThat(finalImages.get(1).getImageUrl()).isEqualTo(newImageUrl);
        assertThat(finalImages.get(1).getSortOrder()).isEqualTo(1);
        assertThat(finalImages.get(1).getSourceType()).isEqualTo(ImageSourceType.PLACE);
        then(imageCommandService).should().deletePlaceImage(first.getImageUrl(), ADMIN_ID, KAKAO_PLACE_ID);
        then(imageCommandService).should().deletePlaceImage(second.getImageUrl(), ADMIN_ID, KAKAO_PLACE_ID);
    }

    @Test
    @DisplayName("다른 장소 사진 ID가 삭제 목록에 있으면 400이다")
    void updatePlace_rejectsForeignImageId() {
        Place place = place(PlaceStatus.APPROVED);
        given(adminPlaceRepository.findAdminPlaceForUpdate(PLACE_ID)).willReturn(Optional.of(place));
        given(placeImageRepository.findAdminImagesByPlaceId(PLACE_ID)).willReturn(List.of());

        assertThatThrownBy(() -> adminPlaceCommandService.updatePlace(
                ADMIN_ID, PLACE_ID, new AdminPlaceUpdateRequest(null, null, null, List.of(999L))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.INVALID_PLACE_IMAGE);

        then(placeImageRepository).should(never()).deleteAll(any());
    }

    @ParameterizedTest
    @EnumSource(value = PlaceStatus.class, names = {"REJECTED", "DELETED"})
    @DisplayName("REJECTED와 DELETED 장소는 수정할 수 없다")
    void updatePlace_rejectsNonEditableStatus(PlaceStatus status) {
        Place place = place(status);
        given(adminPlaceRepository.findAdminPlaceForUpdate(PLACE_ID)).willReturn(Optional.of(place));

        assertThatThrownBy(() -> adminPlaceCommandService.updatePlace(
                ADMIN_ID, PLACE_ID, new AdminPlaceUpdateRequest(null, "새 설명", null, null)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.PLACE_NOT_EDITABLE);

        then(placeImageRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("S3 사진은 DB 트랜잭션이 커밋된 뒤에 삭제한다")
    void updatePlace_deletesS3ObjectAfterCommit() {
        Place place = place(PlaceStatus.APPROVED);
        PlaceImage image = image(place, 11L, "delete-after-commit.jpg", 0);
        AdminPlaceDetailResponse expected = org.mockito.Mockito.mock(AdminPlaceDetailResponse.class);
        given(adminPlaceRepository.findAdminPlaceForUpdate(PLACE_ID)).willReturn(Optional.of(place));
        given(placeImageRepository.findAdminImagesByPlaceId(PLACE_ID)).willReturn(List.of(image));
        given(adminPlaceQueryService.getPlaceDetail(ADMIN_ID, PLACE_ID)).willReturn(expected);

        TransactionSynchronizationManager.initSynchronization();
        try {
            adminPlaceCommandService.updatePlace(
                    ADMIN_ID, PLACE_ID, new AdminPlaceUpdateRequest(null, null, null, List.of(11L)));

            then(imageCommandService).should(never()).deletePlaceImage(anyString(), any(), anyString());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            then(imageCommandService).should()
                    .deletePlaceImage(image.getImageUrl(), ADMIN_ID, KAKAO_PLACE_ID);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("다른 장소가 같은 URL을 참조하면 DB 연결만 지우고 S3 원본은 유지한다")
    void updatePlace_keepsSharedS3Object() {
        Place place = place(PlaceStatus.APPROVED);
        PlaceImage image = image(place, 11L, "shared.jpg", 0);
        AdminPlaceDetailResponse expected = org.mockito.Mockito.mock(AdminPlaceDetailResponse.class);
        given(adminPlaceRepository.findAdminPlaceForUpdate(PLACE_ID)).willReturn(Optional.of(place));
        given(placeImageRepository.findAdminImagesByPlaceId(PLACE_ID)).willReturn(List.of(image));
        given(placeImageRepository.existsByImageUrl(image.getImageUrl())).willReturn(true);
        given(adminPlaceQueryService.getPlaceDetail(ADMIN_ID, PLACE_ID)).willReturn(expected);

        adminPlaceCommandService.updatePlace(
                ADMIN_ID, PLACE_ID, new AdminPlaceUpdateRequest(null, null, null, List.of(11L)));

        then(imageCommandService).should(never()).deletePlaceImage(anyString(), any(), anyString());
    }
}
