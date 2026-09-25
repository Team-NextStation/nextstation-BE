package com.cotato.nextstation.domain.journal.service.command;


import com.cotato.nextstation.domain.course.service.command.CourseCommandService;
import com.cotato.nextstation.domain.journal.dto.request.JournalCreateRequest;
import com.cotato.nextstation.domain.journal.dto.request.JournalUpdateRequest;
import com.cotato.nextstation.domain.journal.enums.ImageAction;
import com.cotato.nextstation.domain.place.dto.request.PlaceReviewCreateRequest;
import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.journal.entity.JournalImage;
import com.cotato.nextstation.domain.journal.exception.JournalErrorCode;
import com.cotato.nextstation.domain.journal.repository.JournalImageRepository;
import com.cotato.nextstation.domain.journal.repository.JournalRepository;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.domain.place.service.command.PlaceReviewCommandService;
import com.cotato.nextstation.domain.stamp.service.query.MemberStampQueryService;

import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.util.ProfanityFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class JournalCommandService {

    private final JournalRepository journalRepository;
    private final MemberRepository memberRepository;
    private final JournalImageRepository journalImageRepository;
    private final PlaceReviewRepository placeReviewRepository;

    private final MemberStampQueryService memberStampQueryService;
    private final PlaceReviewCommandService placeReviewCommandService;
    private final CourseCommandService courseCommandService;
    private final ProfanityFilter profanityFilter;

    // 여행일지 작성
    public Long createJournal(Long memberId, JournalCreateRequest request) {
        validateUserText(request.title());
        validateUserText(request.overallReview());
        validatePlaceReviewTexts(request.placeReviews());
        // memberStamp 소유권 검증 + courseId 확보 (장소 리뷰의 코스 소속 검증, 코스에 일지 연결하는 데 사용)
        Long courseId = memberStampQueryService.getCourseId(memberId, request.memberStampId());

        Member member = memberRepository.getReferenceById(memberId);

        // 2. 같은 스탬프로 이미 작성된 일지가 있는지 확인
        if (journalRepository.existsByMemberStampId(request.memberStampId())) {
            throw new CustomException(JournalErrorCode.JOURNAL_ALREADY_EXISTS);
        }


        // 여행일지 생성
        Journal journal = Journal.builder()
                .member(member)
                .memberStampId(request.memberStampId())
                .title(request.title())
                .overallReview(request.overallReview())
                .traveledAt(request.traveledAt())
                .travelDuration(request.travelDuration())
                .isPublic(request.isPublic())
                .build();

        try {
            journalRepository.save(journal);
        } catch (DataIntegrityViolationException e) {
            log.warn("여행일지 중복 작성 시도(레이스 컨디션): memberId={}, memberStampId={}",
                    memberId, request.memberStampId());
            throw new CustomException(JournalErrorCode.JOURNAL_ALREADY_EXISTS);
        }

        // 대표 사진 저장 (순서대로, 첫 번째가 대표)
        // 프론트가 보낸 imageUrls를 journal에 연결하는 부분
        // S3에는 이미 올라가 있고, 그 URL만 DB에 연결
        if (request.journalImageUrls() != null) {
            request.journalImageUrls().forEach(imageUrl ->
                    journalImageRepository.save(
                            JournalImage.builder()
                                    .journal(journal)
                                    .imageUrl(imageUrl)
                                    .build()
                    )
            );
        }
            // 장소 리뷰 저장
        List<PlaceReviewCreateRequest> reviewRequests =
                request.placeReviews() == null ? List.of() :
                        request.placeReviews().stream()
                                .map(pr -> new PlaceReviewCreateRequest(
                                        pr.placeId(),
                                        pr.review(),
                                        pr.imageUrl()
                                ))
                                .toList();


        placeReviewCommandService.createPlaceReviews(journal, courseId, reviewRequests);

        // 코스에 이 일지를 연결한다. 둘러보기·검색·좋아요·"내 코스로 만들기"가
        // course.journal_id로 공개 여부를 판정하므로, 연결하지 않으면 공개로 써도 어디에도 노출되지 않는다.
        courseCommandService.linkJournal(courseId, journal.getId());

        log.info("여행일지 작성 완료: memberId={}, journalId={}", memberId, journal.getId());

            return journal.getId();
        }

    // 여행일지 수정
    public void updateJournal(Long memberId, Long journalId, JournalUpdateRequest request) {
        validateUserText(request.title());
        validateUserText(request.overallReview());
        Journal journal = findOwnJournal(memberId, journalId);

        // 기본 필드 수정 (null이면 기존값 유지)
        journal.update(
                Optional.ofNullable(request.title()).orElse(journal.getTitle()),
                Optional.ofNullable(request.overallReview()).orElse(journal.getOverallReview()),
                Optional.ofNullable(request.traveledAt()).orElse(journal.getTraveledAt()),
                Optional.ofNullable(request.travelDuration()).orElse(journal.getTravelDuration()),
                Optional.ofNullable(request.isPublic()).orElse(journal.isPublic())
        );


        // 여행일지 사진 수정
        if (request.journalPhotos() != null) {

            request.journalPhotos().forEach(photo -> {
                ImageAction action = photo.imageAction() != null
                        ? photo.imageAction()
                        : ImageAction.KEEP;

                switch (action) {
                    case KEEP -> { /* 유지 */ }
                    case DELETE -> {
                        // journalId 스코프로 소유권 검증 (다른 일지의 사진 삭제 방지)
                        JournalImage journalImage =  journalImageRepository.findByIdAndJournalId(photo.photoId(), journal.getId())
                                .orElseThrow(() -> new CustomException(JournalErrorCode.JOURNAL_IMAGE_NOT_FOUND));

                        journalImage.delete();

                    }
                    case UPDATE -> {
                        // 새 이미지 추가 (photoId 없음). imageUrl이 없으면 DB NOT NULL 제약에 그대로
                        // 부딪혀 500이 나므로, PlaceReviewCommandService.updatePlaceReviews와 같은
                        // 방식으로 여기서 먼저 400을 던진다.
                        if (photo.imageUrl() == null) {
                            throw new CustomException(JournalErrorCode.INVALID_JOURNAL_PHOTO);
                        }
                        journalImageRepository.save(
                                JournalImage.builder()
                                        .journal(journal)
                                        .imageUrl(photo.imageUrl())
                                        .build()
                        );
                    }
                }
            });
        }

        // 장소 리뷰 수정 (보내지 않은 장소는 KEEP으로 간주)
        if (request.placeReviews() != null) {

            placeReviewCommandService.updatePlaceReviews(journal, request.placeReviews());
        }

        log.info("여행일지 수정 완료: memberId={}, journalId={}", memberId, journalId);
    }

    // 여행일지 삭제 -> 리뷰는 삭제하되, 장소 데이터는 DB에 남겨두기
    public void deleteJournal(Long memberId, Long journalId) {
        Journal journal = findOwnJournal(memberId, journalId);

        // 여행일지 대표 사진 soft delete S3 추적을 위해 DB 레코드 유지, 배치로 N일 뒤 정리 예정)
        journalImageRepository.findByJournalId(journalId)
                .forEach(JournalImage::delete);

        // 코스와의 연결을 끊는다. 코스 자체는 일지와 별개라 그대로 남아 "내가 만든 코스"에서 계속 보인다.
        courseCommandService.unlinkJournal(journalId);

        // 여행일지 soft delete
        // PlaceReview/PlaceReviewImage는 유지
        journal.delete();
        log.info("여행일지 삭제 완료: memberId={}, journalId={}", memberId, journalId);
    }

    private Journal findOwnJournal(Long memberId, Long journalId) {
        Journal journal = journalRepository.findById(journalId)
                .orElseThrow(() -> new CustomException(JournalErrorCode.JOURNAL_NOT_FOUND));
        if (!journal.getMember().getId().equals(memberId)) {
            throw new CustomException(JournalErrorCode.JOURNAL_FORBIDDEN);
        }
        return journal;
    }

    private void validatePlaceReviewTexts(List<JournalCreateRequest.PlaceReviewRequest> placeReviews) {
        if (placeReviews != null) {
            placeReviews.forEach(placeReview -> validateUserText(placeReview.review()));
        }
    }

    private void validateUserText(String text) {
        if (profanityFilter.containsBannedWord(text)) {
            throw new CustomException(GlobalErrorCode.CONTAINS_BANNED_WORD);
        }
    }

}
