package com.cotato.nextstation.domain.place.init;

import com.cotato.nextstation.domain.place.entity.Category;
import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.entity.PlaceImage;
import com.cotato.nextstation.domain.place.entity.PlaceTag;
import com.cotato.nextstation.domain.place.entity.PlaceTagMapping;
import com.cotato.nextstation.domain.place.enums.CategoryCode;
import com.cotato.nextstation.domain.place.enums.ImageSourceType;
import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import com.cotato.nextstation.domain.place.repository.CategoryRepository;
import com.cotato.nextstation.domain.place.repository.PlaceImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewLikeRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.domain.place.repository.PlaceTagMappingRepository;
import com.cotato.nextstation.domain.place.repository.PlaceTagRepository;
import com.cotato.nextstation.domain.station.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 시딩 데이터를 DB에 적재하는 컴포넌트.
 *
 * <p>CSV 파싱은 트랜잭션 밖에서 수행하고 저장만 트랜잭션으로 묶기 위해 {@link PlaceSeeder}에서 분리했다.
 * 같은 클래스 안에서 호출하면 {@code @Transactional} 프록시가 적용되지 않으므로 별도 빈으로 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PlaceSeedWriter {

    private static final Map<String, CategoryCode> CATEGORY_CODE_BY_TEXT = Map.of(
            "카페", CategoryCode.CAFE,
            "식당", CategoryCode.FOOD,
            "문화공간", CategoryCode.CULTURE,
            "산책포인트", CategoryCode.WALK
    );

    private static final Map<String, PlaceTagName> PLACE_TAG_BY_TEXT = Map.ofEntries(
            Map.entry("자연과함께", PlaceTagName.NATURE),
            Map.entry("골목여행", PlaceTagName.ALLEY_TRIP),
            Map.entry("시장구경", PlaceTagName.MARKET),
            Map.entry("핫플레이스", PlaceTagName.HOTPLACE),
            Map.entry("사진찍기좋은", PlaceTagName.PHOTO_SPOT),
            Map.entry("쇼핑", PlaceTagName.SHOPPING),
            Map.entry("체험", PlaceTagName.EXPERIENCE),
            Map.entry("가성비", PlaceTagName.BUDGET),
            Map.entry("실내위주", PlaceTagName.INDOOR)
    );

    private final StationRepository stationRepository;
    private final CategoryRepository categoryRepository;
    private final PlaceTagRepository placeTagRepository;
    private final PlaceRepository placeRepository;
    private final PlaceTagMappingRepository placeTagMappingRepository;
    private final PlaceReviewImageRepository placeReviewImageRepository;
    private final PlaceReviewLikeRepository placeReviewLikeRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final PlaceImageRepository placeImageRepository;

    @Transactional
    public void write(List<PlaceSeedRow> rows) {
        // place를 참조하는 자식 테이블부터 삭제한다. 순서를 지키지 않으면 FK 제약에 걸린다.
        placeImageRepository.deleteAllInBatch();
        placeReviewImageRepository.deleteAllInBatch();
        placeReviewLikeRepository.deleteAllInBatch();
        placeReviewRepository.deleteAllInBatch();
        placeTagMappingRepository.deleteAllInBatch();
        placeRepository.deleteAllInBatch();

        Map<String, Category> categoryCache = new HashMap<>();
        Map<PlaceTagName, PlaceTag> tagCache = new HashMap<>();
        int placeCount = 0;
        int tagMappingCount = 0;
        int imageCount = 0;
        int skippedCount = 0;

        for (PlaceSeedRow row : rows) {
            Optional<Long> stationId = stationRepository.findByStationName(row.stationName())
                    .map(station -> station.getId());
            if (stationId.isEmpty()) {
                log.warn("station을 찾을 수 없어 스킵합니다: stationName={}, placeName={}", row.stationName(), row.placeName());
                skippedCount++;
                continue;
            }

            Category category = categoryCache.computeIfAbsent(row.categoryText(), text -> findCategory(text));

            Place place = placeRepository.save(
                    Place.builder()
                            .stationId(stationId.get())
                            .category(category)
                            .description(row.description())
                            .placeName(row.placeName())
                            .address(row.address())
                            .contactNumber(row.contactNumber())
                            .xCoordinate(row.xCoordinate())
                            .yCoordinate(row.yCoordinate())
                            .kakaoPlaceId(row.kakaoPlaceId())
                            .build()
            );
            placeCount++;

            for (String hashtagText : row.hashtagTexts()) {
                PlaceTagName tagName = PLACE_TAG_BY_TEXT.get(hashtagText);
                if (tagName == null) {
                    log.warn("알 수 없는 해시태그 값이라 스킵합니다: {} (placeName={})", hashtagText, row.placeName());
                    continue;
                }

                PlaceTag placeTag = tagCache.computeIfAbsent(tagName, this::findPlaceTag);
                placeTagMappingRepository.save(PlaceTagMapping.of(place, placeTag));
                tagMappingCount++;
            }

            // place-images.csv의 순서는 S3 파일명에 맞춰 1부터 시작한다. sortOrder는 0이 대표 이미지이므로 0부터 매긴다.
            int imageOrder = 0;
            for (PlaceSeedImage image : row.images()) {
                placeImageRepository.save(PlaceImage.builder()
                        .place(place)
                        .imageUrl(image.imageUrl())
                        .source(image.source())
                        .sortOrder(imageOrder++)
                        .sourceType(ImageSourceType.PLACE)
                        .build());
                imageCount++;
            }
        }

        log.info("place 시딩 완료: placeCount={}, tagMappingCount={}, imageCount={}, skippedCount={}",
                placeCount, tagMappingCount, imageCount, skippedCount);
    }

    private Category findCategory(String categoryText) {
        CategoryCode code = CATEGORY_CODE_BY_TEXT.get(categoryText);
        if (code == null) {
            throw new IllegalStateException("알 수 없는 카테고리 값입니다: " + categoryText);
        }
        return categoryRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("category 마스터 데이터가 없습니다: code=" + code));
    }

    private PlaceTag findPlaceTag(PlaceTagName name) {
        return placeTagRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("place_tag 마스터 데이터가 없습니다: name=" + name));
    }
}