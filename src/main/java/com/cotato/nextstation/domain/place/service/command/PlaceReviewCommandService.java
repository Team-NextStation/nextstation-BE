package com.cotato.nextstation.domain.place.service.command;

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

import java.util.List;
import java.util.Map;
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
    private final ProfanityFilter profanityFilter;


    // 여행일지 작성 시 장소 리뷰 일괄 저장
    public void createPlaceReviews(Journal journal, List<PlaceReviewCreateRequest> requests) {
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

        // 리뷰 이미지 일괄 저장
        List<PlaceReviewImage> reviewImages = IntStream.range(0, requests.size())
                .filter(i -> requests.get(i).imageUrl() != null)
                .mapToObj(i -> PlaceReviewImage.builder()
                .placeReview(savedReviews.get(i))
                .imageUrl(requests.get(i).imageUrl())
                        .build())
                .toList();

            if (!reviewImages.isEmpty()) {
                placeReviewImageRepository.saveAll(reviewImages);
            };


            // PlaceReviewImage 저장 실패 시 PlaceReview도 함께 롤백되는 상황
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

            // 텍스트는 항상 반영
            placeReview.update(request.review());

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
