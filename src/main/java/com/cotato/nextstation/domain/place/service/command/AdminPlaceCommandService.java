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
import com.cotato.nextstation.domain.place.entity.PlaceTagMapping;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPlaceCommandService {

    private final AdminGuard adminGuard;
    private final StationRepository stationRepository;
    private final CategoryRepository categoryRepository;
    private final PlaceTagRepository placeTagRepository;
    private final AdminPlaceRepository adminPlaceRepository;
    private final PlaceRepository placeRepository;
    private final PlaceTagMappingRepository placeTagMappingRepository;
    private final PlaceImageRepository placeImageRepository;
    private final ImageCommandService imageCommandService;
    private final AdminPlaceQueryService adminPlaceQueryService;

    @Transactional
    public AdminPlaceCreateResponse createPlace(Long memberId, AdminPlaceCreateRequest request) {
        adminGuard.requireAdmin(memberId);

        if (!stationRepository.existsById(request.stationId())) {
            log.warn("존재하지 않는 역에 장소 등록 시도: memberId={}, stationId={}", memberId, request.stationId());
            throw new CustomException(StationErrorCode.STATION_NOT_FOUND);
        }

        if (placeRepository.existsByStationAndKakaoPlaceIdForAdmin(request.stationId(), request.kakaoPlaceId())) {
            log.warn("이미 등록된 장소 재등록 시도: stationId={}, kakaoPlaceId={}",
                    request.stationId(), request.kakaoPlaceId());
            throw new CustomException(PlaceErrorCode.PLACE_ALREADY_REGISTERED);
        }

        Category category = categoryRepository.findByCode(request.categoryCode())
                .orElseThrow(() -> {
                    log.error("category 마스터 데이터가 없습니다: code={}", request.categoryCode());
                    return new CustomException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
                });

        request.safeImageUrls()
                .forEach(url -> imageCommandService.validatePlaceImageUrl(url, request.kakaoPlaceId()));

        Place place = savePlace(request, category);

        saveTagMappings(place, request.safeTagNames());
        saveImages(place, request.safeImageUrls());

        return new AdminPlaceCreateResponse(place.getId(), place.getStatus());
    }

    @Transactional
    public AdminPlaceDetailResponse updatePlace(Long memberId, Long placeId, AdminPlaceUpdateRequest request) {
        adminGuard.requireAdmin(memberId);

        Place place = adminPlaceRepository.findAdminPlaceForUpdate(placeId)
                .orElseThrow(() -> new CustomException(PlaceErrorCode.PLACE_NOT_FOUND));
        validateEditableStatus(place);

        boolean hasImageUpdates = !request.safeImageUrls().isEmpty() || !request.safeDeleteImageIds().isEmpty();
        List<PlaceImage> currentImages = hasImageUpdates
                ? placeImageRepository.findAdminImagesByPlaceId(placeId)
                : List.of();
        validateNewImages(request.safeImageUrls(), place, currentImages);
        List<PlaceImage> imagesToDelete = resolveImagesToDelete(request.safeDeleteImageIds(), currentImages);
        List<PlaceTag> replacementTags = resolveReplacementTags(request.tagNames());

        if (request.description() != null) {
            place.updateDescription(request.description());
        }
        if (replacementTags != null) {
            replaceTags(place, replacementTags);
        }
        if (hasImageUpdates) {
            updateImages(place, currentImages, imagesToDelete, request.safeImageUrls());
        }

        placeRepository.flush();
        placeTagMappingRepository.flush();
        placeImageRepository.flush();

        return adminPlaceQueryService.getPlaceDetail(memberId, placeId);
    }

    private void validateEditableStatus(Place place) {
        if (place.getStatus() != PlaceStatus.APPROVED && place.getStatus() != PlaceStatus.PENDING) {
            log.warn("수정 불가능한 상태의 장소 수정 시도: placeId={}, status={}", place.getId(), place.getStatus());
            throw new CustomException(PlaceErrorCode.PLACE_NOT_EDITABLE);
        }
    }

    private void validateNewImages(List<String> imageUrls, Place place, List<PlaceImage> currentImages) {
        Set<String> currentImageUrls = currentImages.stream()
                .map(PlaceImage::getImageUrl)
                .collect(Collectors.toSet());
        for (String imageUrl : imageUrls) {
            imageCommandService.validatePlaceImageUrl(imageUrl, place.getKakaoPlaceId());
            if (currentImageUrls.contains(imageUrl)) {
                throw new CustomException(PlaceErrorCode.DUPLICATE_PLACE_IMAGE);
            }
        }
    }

    private List<PlaceImage> resolveImagesToDelete(Set<Long> deleteImageIds, List<PlaceImage> currentImages) {
        if (deleteImageIds.isEmpty()) {
            return List.of();
        }
        Map<Long, PlaceImage> imagesById = currentImages.stream()
                .collect(Collectors.toMap(PlaceImage::getId, Function.identity()));
        if (!imagesById.keySet().containsAll(deleteImageIds)) {
            throw new CustomException(PlaceErrorCode.INVALID_PLACE_IMAGE);
        }
        return deleteImageIds.stream().map(imagesById::get).toList();
    }

    private List<PlaceTag> resolveReplacementTags(List<PlaceTagName> tagNames) {
        if (tagNames == null) {
            return null;
        }
        return tagNames.stream()
                .map(tagName -> placeTagRepository.findByNameAndIsActiveTrue(tagName)
                        .orElseThrow(() -> new CustomException(PlaceErrorCode.INVALID_PLACE_TAG)))
                .toList();
    }

    private void replaceTags(Place place, List<PlaceTag> replacementTags) {
        placeTagMappingRepository.deleteAdminMappingsByPlaceId(place.getId());
        replacementTags.forEach(tag -> placeTagMappingRepository.save(PlaceTagMapping.of(place, tag)));
    }

    private void updateImages(Place place, List<PlaceImage> currentImages,
                              List<PlaceImage> imagesToDelete, List<String> newImageUrls) {
        Set<Long> deleteIds = imagesToDelete.stream().map(PlaceImage::getId).collect(Collectors.toSet());
        List<PlaceImage> finalImages = currentImages.stream()
                .filter(image -> !deleteIds.contains(image.getId()))
                .collect(Collectors.toCollection(ArrayList::new));

        newImageUrls.forEach(imageUrl -> finalImages.add(PlaceImage.builder()
                .place(place)
                .imageUrl(imageUrl)
                .sourceType(ImageSourceType.PLACE)
                .build()));

        // URL 재사용 요청과 S3 물리 삭제가 경합하지 않도록, 여기서는 DB 연결만 제거한다.
        // 참조되지 않는 S3 원본 정리는 별도 배치 작업에서 처리한다.
        placeImageRepository.deleteAll(imagesToDelete);
        for (int index = 0; index < finalImages.size(); index++) {
            finalImages.get(index).updateSortOrder(index);
        }
        placeImageRepository.saveAll(finalImages);
    }

    // 동시 요청으로 uk_place_station_kakao에 걸리는 경우를 flush로 앞당겨 409로 변환한다.
    private Place savePlace(AdminPlaceCreateRequest request, Category category) {
        try {
            return placeRepository.saveAndFlush(
                    Place.builder()
                            .stationId(request.stationId())
                            .category(category)
                            .description(request.description())
                            .placeName(request.placeName())
                            .address(request.address())
                            .contactNumber(request.contactNumber())
                            .xCoordinate(request.xCoordinate())
                            .yCoordinate(request.yCoordinate())
                            .kakaoPlaceId(request.kakaoPlaceId())
                            .status(PlaceStatus.PENDING)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            log.warn("동시 요청으로 중복 장소 등록 시도: stationId={}, kakaoPlaceId={}",
                    request.stationId(), request.kakaoPlaceId(), e);
            throw new CustomException(PlaceErrorCode.PLACE_ALREADY_REGISTERED);
        }
    }

    private void saveTagMappings(Place place, List<PlaceTagName> tagNames) {
        tagNames.stream()
                .distinct()
                .forEach(tagName -> {
                    PlaceTag placeTag = placeTagRepository.findByName(tagName)
                            .orElseThrow(() -> {
                                log.error("place_tag 마스터 데이터가 없습니다: name={}", tagName);
                                return new CustomException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
                            });
                    placeTagMappingRepository.save(PlaceTagMapping.of(place, placeTag));
                });
    }

    private void saveImages(Place place, List<String> imageUrls) {
        int sortOrder = 0;
        for (String imageUrl : imageUrls) {
            placeImageRepository.save(PlaceImage.builder()
                    .place(place)
                    .imageUrl(imageUrl)
                    .sortOrder(sortOrder++)
                    .sourceType(ImageSourceType.PLACE)
                    .build());
        }
    }
}
