package com.cotato.nextstation.domain.place.service.command;

import com.cotato.nextstation.domain.course.dto.response.CoursePlaceInfoResponse;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.domain.journal.dto.request.JournalUpdateRequest;
import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.journal.enums.ImageAction;
import com.cotato.nextstation.domain.place.dto.request.PlaceReviewCreateRequest;
import com.cotato.nextstation.domain.place.dto.request.PlaceReviewUpdateRequest;
import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.entity.PlaceReview;
import com.cotato.nextstation.domain.place.entity.PlaceReviewImage;
import com.cotato.nextstation.domain.place.exception.PlaceErrorCode;
import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.util.ProfanityFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * 장소 리뷰 쓰기 전용 서비스.
 *
 * 여행일지 작성(journal 도메인)은 장소 리뷰(place 도메인)를 함께 저장해야 한다.
 * 이때 JournalCommandService가 PlaceReviewRepository 등 place 도메인의 Repository를
 * 직접 참조하면, place 도메인 내부 구조 변경이 journal 도메인에도 영향을 미친다.
 *
 */

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PlaceReviewCommandService {

    private final PlaceRepository placeRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final PlaceReviewImageRepository placeReviewImageRepository;
    private final CourseQueryService courseQueryService;
    private final PlaceReviewImageSaver placeReviewImageSaver;
    private final ProfanityFilter profanityFilter;


    // 여행일지 작성 시 장소 리뷰 일괄 저장
    public void createPlaceReviews(Journal journal, Long courseId, List<PlaceReviewCreateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        requests.forEach(request -> validateUserText(request.review()));
        // findById + save 조합은 N+1 쿼리 문제가 발생함.
        // findAllById로 배치 조회하고 saveAll로 배치 저장하도록 함

        // placeId 목록 한 번에 조회
        List<Long> placeIds = requests.stream()
                .map(PlaceReviewCreateRequest::placeId)
                .toList();

        // 이 코스에 포함된 장소만 리뷰 작성을 허용한다 (실존 여부와는 별개 검증).
        validatePlacesInCourse(courseId, placeIds);

        Map<Long, Place> placeMap = placeRepository.findAllById(placeIds).stream()
                        .collect(Collectors.toMap(Place::getId, Function.identity()));


        List<PlaceReview> placeReviews = requests.stream()
                .map(request -> {
                    Place place = placeMap.get(request.placeId());
                    if (place == null) {
                        throw new CustomException(PlaceErrorCode.PLACE_NOT_FOUND);
                    }
                    return PlaceReview.builder()
                            .place(place)
                            .journal(journal)
                            .review(request.review())
                            .build();
                })
                .toList();


        // 리뷰 일괄 저장
        List<PlaceReview> savedReviews = placeReviewRepository.saveAll(placeReviews);

        // 이 시점의 PlaceReview/Journal은 이 메서드를 감싼 바깥 트랜잭션이 아직 커밋 전이라
        // 여기서 바로 REQUIRES_NEW로 이미지를 저장하면, 그 새 트랜잭션(=새 커넥션)이 아직
        // 커밋되지 않은 부모 행을 참조하게 돼 락 대기에 걸린다(바깥 트랜잭션은 이 호출이
        // 끝나야 커밋되므로, 사실상 자기 자신을 기다리는 셈이 된다). 그래서 이미지 저장은
        // 바깥 트랜잭션이 실제로 커밋된 뒤(afterCommit)로 미룬다 - 그때는 PlaceReview/Journal이
        // 이미 다른 트랜잭션에서도 보이는 상태라 REQUIRES_NEW가 안전하다.
        registerImageSaveAfterCommit(requests, savedReviews);
    }

    private void registerImageSaveAfterCommit(List<PlaceReviewCreateRequest> requests, List<PlaceReview> savedReviews) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                IntStream.range(0, requests.size())
                        .filter(i -> requests.get(i).imageUrl() != null)
                        .forEach(i -> saveReviewImage(savedReviews.get(i), requests.get(i).imageUrl()));
            }
        });
    }

    // 코스에 속하지 않은 장소는 리뷰를 남길 수 없다. 코스에 속한 placeId 집합과 요청을 대조한다.
    private void validatePlacesInCourse(Long courseId, List<Long> placeIds) {
        Set<Long> coursePlaceIds = courseQueryService.getCoursePlaces(courseId).stream()
                .map(CoursePlaceInfoResponse::placeId)
                .collect(Collectors.toSet());
        placeIds.stream()
                .filter(placeId -> !coursePlaceIds.contains(placeId))
                .findFirst()
                .ifPresent(placeId -> {
                    log.warn("코스에 속하지 않은 장소의 리뷰 작성 시도: courseId={}, placeId={}", courseId, placeId);
                    throw new CustomException(PlaceErrorCode.PLACE_NOT_IN_COURSE);
                });
    }

    /**
     * 이미지 저장 실패는 조용히 삼키고 로그만 남긴다. 사용자에게는 여행일지/리뷰가 정상
     * 저장된 걸로 보여야 하며, 이미지 한 장이 실패했다고 이미 작성한 리뷰까지 잃으면 안 된다.
     * PlaceReviewImageSaver가 REQUIRES_NEW + flush로 이 시점에 예외를 던지도록 보장한다.
     */
    private void saveReviewImage(PlaceReview placeReview, String imageUrl) {
        try {
            placeReviewImageSaver.save(placeReview, imageUrl);
        } catch (Exception e) {
            log.warn("장소 리뷰 이미지 저장 실패: placeReviewId={}, imageUrl={}", placeReview.getId(), imageUrl, e);
        }
    }

    // 여행일지 수정 시 장소 리뷰 수정
    public void updatePlaceReviews(Journal journal, List<PlaceReviewUpdateRequest> requests) {
        // 보내지 않은 장소는 KEEP으로 간주 → 온 것만 처리
        requests.forEach(request -> validateUserText(request.review()));
        requests.forEach(request -> {
            PlaceReview placeReview = placeReviewRepository
                    .findByJournalIdAndPlaceId(journal.getId(), request.placeId())
                    .orElseThrow(() -> {
                        log.warn("수정 요청한 장소 리뷰가 존재하지 않음: journalId={}, placeId={}",
                                journal.getId(), request.placeId());
                        return new CustomException(PlaceErrorCode.PLACE_REVIEW_NOT_FOUND);
                    });

            // review가 null이면 유지(사진만 바꾸는 요청을 위해 기존 텍스트를 다시 채워 보낼 필요가 없게 함),
            // 빈 문자열("")을 명시적으로 보내면 텍스트를 비운다.
            if (request.review() != null) {
                placeReview.update(request.review());
            }

            // imageAction null이면 KEEP으로 간주
            ImageAction action = request.imageAction() != null
                    ? request.imageAction()
                    : ImageAction.KEEP;

            switch (action) {
                case KEEP -> {
                    // 이미지 유지, 아무것도 안 함
                }
                case DELETE -> {
                    // 리뷰 이미지만 DB에서 삭제 (S3는 배치 잡으로 정리)
                    placeReviewImageRepository.deleteByPlaceReviewId(placeReview.getId());
                }
                case UPDATE -> {
                    // 기존 이미지 삭제 후 새 이미지 저장
                    if (request.imageUrl() == null) {
                        throw new CustomException(PlaceErrorCode.INVALID_PLACE_REVIEW_IMAGE);
                    }

                    placeReviewImageRepository.deleteByPlaceReviewId(placeReview.getId());
                    placeReviewImageRepository.save(
                            PlaceReviewImage.builder()
                                    .placeReview(placeReview)
                                    .imageUrl(request.imageUrl())
                                    .build()
                    );
                }
            }
        });

        log.info("장소 리뷰 수정 완료: journalId={}, count={}", journal.getId(), requests.size());
    }

    private void validateUserText(String text) {
        if (profanityFilter.containsBannedWord(text)) {
            throw new CustomException(GlobalErrorCode.CONTAINS_BANNED_WORD);
        }
    }
}
