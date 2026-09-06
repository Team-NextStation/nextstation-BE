package com.cotato.nextstation.domain.place.service.command;

import com.cotato.nextstation.domain.image.service.command.ImageCommandService;
import com.cotato.nextstation.domain.member.service.query.AdminGuard;
import com.cotato.nextstation.domain.place.dto.request.AdminPlaceCreateRequest;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCreateResponse;
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
import com.cotato.nextstation.domain.place.repository.PlaceImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceTagMappingRepository;
import com.cotato.nextstation.domain.place.repository.PlaceTagRepository;
import com.cotato.nextstation.domain.station.exception.StationErrorCode;
import com.cotato.nextstation.domain.station.repository.StationRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPlaceCommandService {

    private final AdminGuard adminGuard;
    private final StationRepository stationRepository;
    private final CategoryRepository categoryRepository;
    private final PlaceTagRepository placeTagRepository;
    private final PlaceRepository placeRepository;
    private final PlaceTagMappingRepository placeTagMappingRepository;
    private final PlaceImageRepository placeImageRepository;
    private final ImageCommandService imageCommandService;

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

        request.safeImageUrls().forEach(imageCommandService::validatePlaceImageUrl);

        Place place = placeRepository.save(
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

        saveTagMappings(place, request.safeTagNames());
        saveImages(place, request.safeImageUrls());

        log.info("장소 등록 완료: memberId={}, placeId={}, stationId={}, tagCount={}, imageCount={}",
                memberId, place.getId(), request.stationId(),
                request.safeTagNames().size(), request.safeImageUrls().size());

        return new AdminPlaceCreateResponse(place.getId(), place.getStatus());
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
