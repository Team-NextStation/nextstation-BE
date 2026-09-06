package com.cotato.nextstation.domain.place.service.command;

import com.cotato.nextstation.domain.image.service.command.ImageCommandService;
import com.cotato.nextstation.domain.member.service.query.AdminGuard;
import com.cotato.nextstation.domain.place.dto.request.AdminPlaceCreateRequest;
import com.cotato.nextstation.domain.place.dto.request.AdminPlaceStatusUpdateRequest;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCreateResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceStatusUpdateResponse;
import com.cotato.nextstation.domain.place.entity.Category;
import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.entity.PlaceImage;
import com.cotato.nextstation.domain.place.entity.PlaceTag;
import com.cotato.nextstation.domain.place.entity.PlaceTagMapping;
import com.cotato.nextstation.domain.place.enums.ImageSourceType;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import com.cotato.nextstation.domain.place.exception.PlaceErrorCode;
import com.cotato.nextstation.domain.place.repository.AdminPlaceRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPlaceCommandService {

    private static final Map<PlaceStatus, Set<PlaceStatus>> ALLOWED_TRANSITIONS = Map.of(
            PlaceStatus.PENDING, Set.of(PlaceStatus.APPROVED, PlaceStatus.REJECTED, PlaceStatus.DELETED),
            PlaceStatus.APPROVED, Set.of(PlaceStatus.DELETED),
            PlaceStatus.REJECTED, Set.of(PlaceStatus.PENDING),
            PlaceStatus.DELETED, Set.of(PlaceStatus.PENDING)
    );

    private static final Set<PlaceStatus> REASON_REQUIRED_STATUSES =
            Set.of(PlaceStatus.REJECTED, PlaceStatus.DELETED);

    private final AdminGuard adminGuard;
    private final AdminPlaceRepository adminPlaceRepository;
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

        request.safeImageUrls()
                .forEach(url -> imageCommandService.validatePlaceImageUrl(url, request.kakaoPlaceId()));

        Place place = savePlace(request, category);

        saveTagMappings(place, request.safeTagNames());
        saveImages(place, request.safeImageUrls());

        return new AdminPlaceCreateResponse(place.getId(), place.getStatus());
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

    @Transactional
    public AdminPlaceStatusUpdateResponse updateStatus(Long memberId, Long placeId,
                                                       AdminPlaceStatusUpdateRequest request) {
        adminGuard.requireAdmin(memberId);

        Place place = adminPlaceRepository.findAdminPlaceById(placeId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 장소의 상태 변경 시도: placeId={}", placeId);
                    return new CustomException(PlaceErrorCode.PLACE_NOT_FOUND);
                });

        PlaceStatus target = request.status();
        if (!ALLOWED_TRANSITIONS.getOrDefault(place.getStatus(), Set.of()).contains(target)) {
            log.warn("허용되지 않는 상태 전이 요청: placeId={}, current={}, target={}",
                    placeId, place.getStatus(), target);
            throw new CustomException(PlaceErrorCode.INVALID_PLACE_STATUS_TRANSITION);
        }

        String reason = request.reason();
        if (REASON_REQUIRED_STATUSES.contains(target) && !StringUtils.hasText(reason)) {
            log.warn("사유 없이 상태 변경 요청: placeId={}, target={}", placeId, target);
            throw new CustomException(PlaceErrorCode.PLACE_STATUS_REASON_REQUIRED);
        }

        place.changeStatus(target, reason);
        log.info("장소 상태 변경 완료: placeId={}, target={}", placeId, target);

        return new AdminPlaceStatusUpdateResponse(placeId, target);
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
