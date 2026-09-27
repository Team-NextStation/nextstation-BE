package com.cotato.nextstation.domain.place.service.command;

import com.cotato.nextstation.domain.course.dto.response.CoursePlaceInfoResponse;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.place.dto.request.PlaceReviewCreateRequest;
import com.cotato.nextstation.domain.place.dto.request.PlaceReviewUpdateRequest;
import com.cotato.nextstation.domain.place.entity.Place;
import com.cotato.nextstation.domain.place.entity.PlaceReview;
import com.cotato.nextstation.domain.place.exception.PlaceErrorCode;
import com.cotato.nextstation.domain.place.repository.PlaceRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.util.ProfanityFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PlaceReviewCommandServiceTest {

    @InjectMocks
    private PlaceReviewCommandService placeReviewCommandService;

    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private PlaceReviewRepository placeReviewRepository;
    @Mock
    private PlaceReviewImageRepository placeReviewImageRepository;
    @Mock
    private CourseQueryService courseQueryService;
    @Mock
    private PlaceReviewImageSaver placeReviewImageSaver;
    @Mock
    private ProfanityFilter profanityFilter;

    private static final Long COURSE_ID = 1L;
    private static final Long PLACE_ID = 10L;

    // 이미지 저장은 이제 트랜잭션 afterCommit에 등록됐다가 실행되므로, 등록이 가능하려면
    // 스레드에 트랜잭션 동기화가 "활성" 상태여야 한다. 실제 커밋은 없는 순수 단위 테스트라
    // 여기서 직접 동기화를 켜고, 커밋 시점은 triggerAfterCommit()으로 흉내낸다.
    @BeforeEach
    void setUpTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDownTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Journal journalFixture() {
        return mock(Journal.class);
    }

    private Place placeFixture(Long placeId) {
        Place place = mock(Place.class);
        given(place.getId()).willReturn(placeId);
        return place;
    }

    // 코스에 이 placeId가 포함돼 있는 상태로 스텁한다
    private void stubPlaceInCourse(Long placeId) {
        given(courseQueryService.getCoursePlaces(COURSE_ID))
                .willReturn(List.of(new CoursePlaceInfoResponse(placeId, 1)));
    }

    // saveAll이 넘어온 리스트를 그대로 돌려주도록 스텁한다 (실제 DB처럼 저장 후 그대로 반환)
    private void stubSaveAllReturnsInput() {
        given(placeReviewRepository.saveAll(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("금칙어가 포함된 장소 리뷰를 작성하면 저장하지 않고 400 예외가 발생한다")
    void createPlaceReviews_bannedReview() {
        given(profanityFilter.containsBannedWord("금칙어")).willReturn(true);

        assertThatThrownBy(() -> placeReviewCommandService.createPlaceReviews(
                journalFixture(), COURSE_ID, List.of(new PlaceReviewCreateRequest(PLACE_ID, "금칙어", null))))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(GlobalErrorCode.CONTAINS_BANNED_WORD.getMessage());

        verifyNoInteractions(courseQueryService, placeRepository, placeReviewRepository, placeReviewImageRepository, placeReviewImageSaver);
    }

    @Test
    @DisplayName("금칙어가 포함된 장소 리뷰를 수정하면 변경하지 않고 400 예외가 발생한다")
    void updatePlaceReviews_bannedReview() {
        given(profanityFilter.containsBannedWord("금칙어")).willReturn(true);

        assertThatThrownBy(() -> placeReviewCommandService.updatePlaceReviews(
                journalFixture(), List.of(new PlaceReviewUpdateRequest(PLACE_ID, "금칙어", null, null))))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(GlobalErrorCode.CONTAINS_BANNED_WORD.getMessage());

        verifyNoInteractions(placeReviewRepository, placeReviewImageRepository, placeReviewImageSaver);
    }

    @Test
    @DisplayName("코스에 포함되고 실존하는 장소면 리뷰와 이미지를 저장한다")
    void createPlaceReviews_success() {
        // given
        Journal journal = journalFixture();
        Place place = placeFixture(PLACE_ID);
        stubPlaceInCourse(PLACE_ID);
        given(placeRepository.findAllById(List.of(PLACE_ID))).willReturn(List.of(place));
        stubSaveAllReturnsInput();

        List<PlaceReviewCreateRequest> requests =
                List.of(new PlaceReviewCreateRequest(PLACE_ID, "좋았어요", "image.jpg"));

        // when
        placeReviewCommandService.createPlaceReviews(journal, COURSE_ID, requests);
        TransactionSynchronizationUtils.triggerAfterCommit(); // 이미지 저장은 커밋 후 실행되므로 커밋을 흉내낸다

        // then
        ArgumentCaptor<List<PlaceReview>> captor = ArgumentCaptor.forClass(List.class);
        verify(placeReviewRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);

        verify(placeReviewImageSaver).save(any(PlaceReview.class), eq("image.jpg"));
    }

    @Test
    @DisplayName("이미지 URL이 없으면 이미지 저장을 시도하지 않는다")
    void createPlaceReviews_noImageUrl_skipsImageSave() {
        // given
        Journal journal = journalFixture();
        Place place = placeFixture(PLACE_ID);
        stubPlaceInCourse(PLACE_ID);
        given(placeRepository.findAllById(List.of(PLACE_ID))).willReturn(List.of(place));
        stubSaveAllReturnsInput();

        List<PlaceReviewCreateRequest> requests =
                List.of(new PlaceReviewCreateRequest(PLACE_ID, "좋았어요", null));

        // when
        placeReviewCommandService.createPlaceReviews(journal, COURSE_ID, requests);
        TransactionSynchronizationUtils.triggerAfterCommit();

        // then
        verify(placeReviewImageSaver, never()).save(any(), any());
    }

    @Test
    @DisplayName("요청이 null이거나 비어 있으면 아무 것도 하지 않는다")
    void createPlaceReviews_emptyRequests_noop() {
        // when & then
        placeReviewCommandService.createPlaceReviews(journalFixture(), COURSE_ID, null);
        placeReviewCommandService.createPlaceReviews(journalFixture(), COURSE_ID, List.of());

        verifyNoInteractions(courseQueryService, placeRepository, placeReviewRepository, placeReviewImageSaver);
    }

    @Test
    @DisplayName("코스에 속하지 않은 장소면 PLACE_NOT_IN_COURSE 예외가 발생하고 아무 것도 저장하지 않는다")
    void createPlaceReviews_placeNotInCourse_throwsException() {
        // given: 코스에는 다른 장소만 있고, 요청한 placeId는 없다
        given(courseQueryService.getCoursePlaces(COURSE_ID))
                .willReturn(List.of(new CoursePlaceInfoResponse(999L, 1)));

        List<PlaceReviewCreateRequest> requests =
                List.of(new PlaceReviewCreateRequest(PLACE_ID, "좋았어요", null));

        // when & then
        assertThatThrownBy(() -> placeReviewCommandService.createPlaceReviews(journalFixture(), COURSE_ID, requests))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(PlaceErrorCode.PLACE_NOT_IN_COURSE.getMessage());

        verify(placeRepository, never()).findAllById(any());
        verify(placeReviewRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("코스에는 속하지만 DB에 존재하지 않는 장소면 PLACE_NOT_FOUND 예외가 발생한다")
    void createPlaceReviews_placeNotFoundInDb_throwsException() {
        // given: 코스 소속 검증은 통과하지만 실제 장소 테이블에는 없음
        stubPlaceInCourse(PLACE_ID);
        given(placeRepository.findAllById(List.of(PLACE_ID))).willReturn(List.of());

        List<PlaceReviewCreateRequest> requests =
                List.of(new PlaceReviewCreateRequest(PLACE_ID, "좋았어요", null));

        // when & then
        assertThatThrownBy(() -> placeReviewCommandService.createPlaceReviews(journalFixture(), COURSE_ID, requests))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(PlaceErrorCode.PLACE_NOT_FOUND.getMessage());

        verify(placeReviewRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("이미지 저장이 실패해도 예외가 전파되지 않고 리뷰는 이미 저장된 상태로 남는다")
    void createPlaceReviews_imageSaveFails_doesNotPropagate() {
        // given
        Place place = placeFixture(PLACE_ID);
        stubPlaceInCourse(PLACE_ID);
        given(placeRepository.findAllById(List.of(PLACE_ID))).willReturn(List.of(place));
        stubSaveAllReturnsInput();
        doThrow(new RuntimeException("DB 제약조건 위반")).when(placeReviewImageSaver).save(any(), any());

        List<PlaceReviewCreateRequest> requests =
                List.of(new PlaceReviewCreateRequest(PLACE_ID, "좋았어요", "broken-image.jpg"));

        // when & then: 이미지 저장 실패가 상위로 전파되지 않는다
        assertThatCode(() -> {
            placeReviewCommandService.createPlaceReviews(journalFixture(), COURSE_ID, requests);
            TransactionSynchronizationUtils.triggerAfterCommit();
        }).doesNotThrowAnyException();

        // 리뷰 저장은 이미지 저장보다 먼저 이뤄져, 이미지 실패와 무관하게 호출된다
        verify(placeReviewRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("여러 이미지 중 하나가 실패해도 나머지 이미지는 각각 별도로 저장을 시도한다")
    void createPlaceReviews_oneImageFails_othersStillAttempted() {
        // given
        Long placeId2 = 20L;
        Place place1 = placeFixture(PLACE_ID);
        Place place2 = placeFixture(placeId2);
        given(courseQueryService.getCoursePlaces(COURSE_ID)).willReturn(List.of(
                new CoursePlaceInfoResponse(PLACE_ID, 1),
                new CoursePlaceInfoResponse(placeId2, 2)));
        given(placeRepository.findAllById(List.of(PLACE_ID, placeId2))).willReturn(List.of(place1, place2));
        stubSaveAllReturnsInput();
        doThrow(new RuntimeException("실패"))
                .when(placeReviewImageSaver).save(any(), eq("broken.jpg"));

        List<PlaceReviewCreateRequest> requests = List.of(
                new PlaceReviewCreateRequest(PLACE_ID, "리뷰1", "broken.jpg"),
                new PlaceReviewCreateRequest(placeId2, "리뷰2", "ok.jpg"));

        // when
        placeReviewCommandService.createPlaceReviews(journalFixture(), COURSE_ID, requests);
        TransactionSynchronizationUtils.triggerAfterCommit();

        // then: 실패한 이미지 이후에도 다음 이미지 저장이 계속 시도된다
        verify(placeReviewImageSaver, times(2)).save(any(), any());
        verify(placeReviewImageSaver).save(any(), eq("ok.jpg"));
    }
}
