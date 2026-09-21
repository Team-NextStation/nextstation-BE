package com.cotato.nextstation.domain.journal.service.command;

import com.cotato.nextstation.domain.course.service.command.CourseCommandService;
import com.cotato.nextstation.domain.journal.dto.request.JournalCreateRequest;
import com.cotato.nextstation.domain.journal.dto.request.JournalUpdateRequest;
import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.journal.entity.JournalImage;
import com.cotato.nextstation.domain.journal.enums.ImageAction;
import com.cotato.nextstation.domain.journal.enums.TravelDuration;
import com.cotato.nextstation.domain.journal.repository.JournalImageRepository;
import com.cotato.nextstation.domain.journal.repository.JournalRepository;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.repository.MemberRepository;
import com.cotato.nextstation.domain.place.dto.request.PlaceReviewUpdateRequest;
import com.cotato.nextstation.domain.place.service.command.PlaceReviewCommandService;
import com.cotato.nextstation.domain.stamp.service.query.MemberStampQueryService;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.util.ProfanityFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * JournalCommandService 테스트.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JournalCommandServiceTest {

    @Mock
    private JournalRepository journalRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private JournalImageRepository journalImageRepository;
    @Mock
    private MemberStampQueryService memberStampQueryService;
    @Mock
    private PlaceReviewCommandService placeReviewCommandService;
    @Mock
    private CourseCommandService courseCommandService;
    @Mock
    private ProfanityFilter profanityFilter;

    @InjectMocks
    private JournalCommandService journalCommandService;

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long MEMBER_STAMP_ID = 100L;
    private static final Long JOURNAL_ID = 10L;
    private static final Long COURSE_ID = 999L;

    private Member member;

    @BeforeEach
    void setUp() {
        member = mock(Member.class);
        when(member.getId()).thenReturn(MEMBER_ID);
        when(memberRepository.getReferenceById(MEMBER_ID)).thenReturn(member);
    }

    private Journal createJournalFixture() {
        Journal journal = Journal.builder()
                .member(member)
                .memberStampId(MEMBER_STAMP_ID)
                .title("보문 골목 산책")
                .overallReview("날씨가 좋아서 걷기 좋았다.")
                .traveledAt(LocalDate.of(2026, 7, 8))
                .travelDuration(TravelDuration.HALF_DAY)
                .isPublic(true)
                .build();
        ReflectionTestUtils.setField(journal, "id", JOURNAL_ID);
        return journal;
    }

    private JournalCreateRequest createRequest(List<String> imageUrls,
                                               List<JournalCreateRequest.PlaceReviewRequest> placeReviews) {
        return new JournalCreateRequest(
                MEMBER_STAMP_ID,
                "보문 골목 산책",
                "날씨가 좋아서 걷기 좋았다.",
                LocalDate.of(2026, 7, 8),
                TravelDuration.HALF_DAY,
                true,
                imageUrls,
                placeReviews
        );
    }

    @Nested
    @DisplayName("createJournal")
    class CreateJournal {

        @Test
        @DisplayName("정상적으로 여행일지를 작성한다")
        void createJournal_success() {
            // given
            when(memberStampQueryService.getCourseId(MEMBER_ID, MEMBER_STAMP_ID)).thenReturn(COURSE_ID);
            when(memberRepository.getReferenceById(MEMBER_ID)).thenReturn(member);
            when(journalRepository.existsByMemberStampId(MEMBER_STAMP_ID)).thenReturn(false);
            // 저장 시 id가 부여되는 실제 동작을 흉내낸다. 코스에 연결되는 값이 이 id여야 한다.
            when(journalRepository.save(any(Journal.class))).thenAnswer(invocation -> {
                Journal saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", JOURNAL_ID);
                return saved;
            });

            JournalCreateRequest request = createRequest(
                    List.of("image1.jpg", "image2.jpg"),
                    List.of(new JournalCreateRequest.PlaceReviewRequest(10L, "커피가 맛있었어요.", "review1.jpg"))
            );

            // when
            journalCommandService.createJournal(MEMBER_ID, request);

            // then
            verify(journalRepository).save(any(Journal.class));
            verify(journalImageRepository, times(2)).save(any(JournalImage.class));
            verify(placeReviewCommandService).createPlaceReviews(any(Journal.class), argThat(reviews -> reviews.size() == 1));
            // 연결하지 않으면 공개로 써도 둘러보기·검색·좋아요·복제에서 공개 코스로 잡히지 않는다
            verify(courseCommandService).linkJournal(COURSE_ID, JOURNAL_ID);
        }

        @Test
        @DisplayName("이미 같은 스탬프로 작성된 여행일지가 있으면 JOURNAL_ALREADY_EXISTS 예외가 발생한다")
        void createJournal_duplicateStamp_throwsException() {
            // given
            when(memberStampQueryService.getCourseId(MEMBER_ID, MEMBER_STAMP_ID)).thenReturn(COURSE_ID);
            when(journalRepository.existsByMemberStampId(MEMBER_STAMP_ID)).thenReturn(true);

            JournalCreateRequest request = createRequest(null, null);

            // when & then
            assertThatThrownBy(() -> journalCommandService.createJournal(MEMBER_ID, request))
                    .isInstanceOf(CustomException.class);

            verify(journalRepository, never()).save(any());
            verify(placeReviewCommandService, never()).createPlaceReviews(any(), any());
            // 일지가 만들어지지 않았으므로 코스에도 연결되면 안 된다
            verify(courseCommandService, never()).linkJournal(any(), any());
        }

        @Test
        @DisplayName("본인 소유가 아닌(또는 존재하지 않는) 스탬프면 memberStampQueryService에서 예외가 전파된다")
        void createJournal_stampNotOwned_propagatesException() {
            // given
            when(memberStampQueryService.getCourseId(MEMBER_ID, MEMBER_STAMP_ID))
                    .thenThrow(new CustomException(com.cotato.nextstation.domain.stamp.exception.StampErrorCode.MEMBER_STAMP_NOT_FOUND));

            JournalCreateRequest request = createRequest(null, null);

            // when & then
            assertThatThrownBy(() -> journalCommandService.createJournal(MEMBER_ID, request))
                    .isInstanceOf(CustomException.class);

            verify(journalRepository, never()).existsByMemberStampId(any());
            verify(journalRepository, never()).save(any());
        }

        @Test
        @DisplayName("journalImageUrls가 null이면 이미지 저장을 시도하지 않는다")
        void createJournal_nullImageUrls_skipsImageSave() {
            // given
            when(memberStampQueryService.getCourseId(MEMBER_ID, MEMBER_STAMP_ID)).thenReturn(COURSE_ID);
            when(memberRepository.getReferenceById(MEMBER_ID)).thenReturn(member);
            when(journalRepository.existsByMemberStampId(MEMBER_STAMP_ID)).thenReturn(false);

            JournalCreateRequest request = createRequest(null, null);

            // when
            journalCommandService.createJournal(MEMBER_ID, request);

            // then
            verify(journalImageRepository, never()).save(any());
            // placeReviews가 null이면 빈 리스트로 변환되어 호출된다
            verify(placeReviewCommandService).createPlaceReviews(any(Journal.class), eq(List.of()));
        }
    }

    @Nested
    @DisplayName("updateJournal")
    class UpdateJournal {

        @Test
        @DisplayName("전달된 필드만 반영하고 null인 필드는 기존 값을 유지한다")
        void updateJournal_partialUpdate_keepsExistingValuesForNullFields() {
            // given
            Journal journal = createJournalFixture();
            when(journalRepository.findById(JOURNAL_ID)).thenReturn(Optional.of(journal));

            JournalUpdateRequest request = new JournalUpdateRequest(
                    "새로운 제목",
                    null, // overallReview는 기존 값 유지되어야 함
                    null, // traveledAt도 기존 값 유지
                    null,
                    null,
                    null,
                    null
            );

            // when
            journalCommandService.updateJournal(MEMBER_ID, JOURNAL_ID, request);

            // then
            assertThat(journal.getTitle()).isEqualTo("새로운 제목");
            assertThat(journal.getOverallReview()).isEqualTo("날씨가 좋아서 걷기 좋았다."); // 유지됨
            assertThat(journal.getTraveledAt()).isEqualTo(LocalDate.of(2026, 7, 8)); // 유지됨
            assertThat(journal.getTravelDuration()).isEqualTo(TravelDuration.HALF_DAY); // 유지됨
            assertThat(journal.isPublic()).isTrue(); // 유지됨
        }

        @Test
        @DisplayName("존재하지 않는 여행일지를 수정하려 하면 JOURNAL_NOT_FOUND 예외가 발생한다")
        void updateJournal_notFound_throwsException() {
            // given
            when(journalRepository.findById(JOURNAL_ID)).thenReturn(Optional.empty());
            JournalUpdateRequest request = new JournalUpdateRequest(null, null, null, null, null, null, null);

            // when & then
            CustomException exception = catchThrowableOfType(
                    () -> journalCommandService.updateJournal(MEMBER_ID, JOURNAL_ID, request),
                    CustomException.class);

            assertThat(exception).isNotNull();
        }

        @Test
        @DisplayName("본인 소유가 아닌 여행일지를 수정하려 하면 JOURNAL_FORBIDDEN 예외가 발생한다")
        void updateJournal_notOwner_throwsForbidden() {
            // given
            Journal journal = createJournalFixture(); // member.getId() == MEMBER_ID
            when(journalRepository.findById(JOURNAL_ID)).thenReturn(Optional.of(journal));
            JournalUpdateRequest request = new JournalUpdateRequest(null, null, null, null, null, null, null);

            // when & then
            assertThatThrownBy(() -> journalCommandService.updateJournal(OTHER_MEMBER_ID, JOURNAL_ID, request))
                    .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("본인 일지의 사진을 삭제하면 성공한다")
        void updateJournal_photoDelete_success() {
            // given
            Journal journal = createJournalFixture();

            when(journalRepository.findById(JOURNAL_ID))
                    .thenReturn(Optional.of(journal));

            ReflectionTestUtils.setField(journal, "id", JOURNAL_ID);

            JournalImage journalImage = JournalImage.builder()
                    .journal(journal)
                    .imageUrl("image.jpg")
                    .build();

            Long photoId = 55L;

            when(journalImageRepository.findByIdAndJournalId(photoId, JOURNAL_ID))
                    .thenReturn(Optional.of(journalImage));

            JournalUpdateRequest.JournalPhotoUpdateRequest deletePhoto =
                    new JournalUpdateRequest.JournalPhotoUpdateRequest(
                            photoId,
                            ImageAction.DELETE,
                            null
                    );

            JournalUpdateRequest request = new JournalUpdateRequest(
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(deletePhoto),
                    null
            );

            // when
            journalCommandService.updateJournal(MEMBER_ID, JOURNAL_ID, request);

            // then
            assertThat(journalImage.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("journalPhotos의 UPDATE 액션이면 새 이미지를 저장한다")
        void updateJournal_photoUpdate_savesNewImage() {
            // given
            Journal journal = createJournalFixture();
            when(journalRepository.findById(JOURNAL_ID)).thenReturn(Optional.of(journal));

            JournalUpdateRequest.JournalPhotoUpdateRequest newPhoto =
                    new JournalUpdateRequest.JournalPhotoUpdateRequest(null, ImageAction.UPDATE, "new.jpg");
            JournalUpdateRequest request = new JournalUpdateRequest(
                    null, null, null, null, null, List.of(newPhoto), null);

            // when
            journalCommandService.updateJournal(MEMBER_ID, JOURNAL_ID, request);

            // then
            verify(journalImageRepository).save(any(JournalImage.class));
        }

        @Test
        @DisplayName("imageAction이 null이면 KEEP으로 간주하여 예외 없이 통과한다")
        void updateJournal_photoActionNull_treatedAsKeep() {
            // given
            Journal journal = createJournalFixture();
            when(journalRepository.findById(JOURNAL_ID))
                    .thenReturn(Optional.of(journal));

            JournalUpdateRequest.JournalPhotoUpdateRequest nullActionPhoto =
                    new JournalUpdateRequest.JournalPhotoUpdateRequest(
                            null,
                            null,  // imageAction = null
                            null
                    );

            JournalUpdateRequest request = new JournalUpdateRequest(
                    null, null, null, null, null,
                    List.of(nullActionPhoto),
                    null
            );

            // when & then — 예외 없이 정상 통과
            assertThatCode(() -> journalCommandService.updateJournal(MEMBER_ID, JOURNAL_ID, request))
                    .doesNotThrowAnyException();

            // 아무 저장/삭제도 호출되지 않음 (KEEP이므로)
            verifyNoInteractions(journalImageRepository);
        }

        @Test
        @DisplayName("placeReviews가 전달되면 PlaceReviewCommandService에 위임한다")
        void updateJournal_delegatesPlaceReviewsUpdate() {
            // given
            Journal journal = createJournalFixture();
            when(journalRepository.findById(JOURNAL_ID)).thenReturn(Optional.of(journal));

            List<PlaceReviewUpdateRequest> placeReviews = List.of(
                    new PlaceReviewUpdateRequest(10L, "수정된 리뷰", ImageAction.KEEP, null));
            JournalUpdateRequest request = new JournalUpdateRequest(
                    null, null, null, null, null, null, placeReviews);

            // when
            journalCommandService.updateJournal(MEMBER_ID, JOURNAL_ID, request);

            // then
            verify(placeReviewCommandService).updatePlaceReviews(journal, placeReviews);
        }

        @Test
        @DisplayName("존재하지 않는 photoId로 삭제하면 JOURNAL_IMAGE_NOT_FOUND 예외가 발생한다")
        void updateJournal_deletePhoto_notFound() {
            // given
            Journal journal = createJournalFixture();

            when(journalRepository.findById(JOURNAL_ID))
                    .thenReturn(Optional.of(journal));

            Long photoId = 999L;

            when(journalImageRepository.findByIdAndJournalId(photoId, JOURNAL_ID))
                    .thenReturn(Optional.empty());

            JournalUpdateRequest.JournalPhotoUpdateRequest deletePhoto =
                    new JournalUpdateRequest.JournalPhotoUpdateRequest(
                            photoId,
                            ImageAction.DELETE,
                            null
                    );

            JournalUpdateRequest request = new JournalUpdateRequest(
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(deletePhoto),
                    null
            );

            // when & then
            assertThatThrownBy(() ->
                    journalCommandService.updateJournal(MEMBER_ID, JOURNAL_ID, request)
            )
                    .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("deleteJournal")
    class DeleteJournal {

        @Test
        @DisplayName("정상적으로 삭제하면 대표 사진을 지우고 소프트 삭제 처리한다")
        void deleteJournal_success() {
            // given
            Journal journal = createJournalFixture();
            when(journalRepository.findById(JOURNAL_ID)).thenReturn(Optional.of(journal));

            // when
            journalCommandService.deleteJournal(MEMBER_ID, JOURNAL_ID);

            // then
            verify(journalImageRepository).findByJournalId(JOURNAL_ID);
            assertThat(journal.isDeleted()).isTrue();
            assertThat(journal.getDeletedAt()).isNotNull();

            // 연결을 끊지 않으면 삭제된 일지를 가리킨 채로 둘러보기 조회에 걸린다
            verify(courseCommandService).unlinkJournal(JOURNAL_ID);

            // PlaceReview/PlaceReviewImage는 건드리지 않음 (의도한 동작)
            // journal.isDeleted=true + @SQLRestriction으로 장소 상세 조회에서 자동 필터링됨
            verifyNoInteractions(placeReviewCommandService);
        }

        @Test
        @DisplayName("존재하지 않는 여행일지를 삭제하려 하면 JOURNAL_NOT_FOUND 예외가 발생한다")
        void deleteJournal_notFound_throwsException() {
            // given
            when(journalRepository.findById(JOURNAL_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> journalCommandService.deleteJournal(MEMBER_ID, JOURNAL_ID))
                    .isInstanceOf(CustomException.class);

            verify(journalImageRepository, never()).findByJournalId(any());
        }

        @Test
        @DisplayName("본인 소유가 아닌 여행일지를 삭제하려 하면 JOURNAL_FORBIDDEN 예외가 발생한다")
        void deleteJournal_notOwner_throwsForbidden() {
            // given
            Journal journal = createJournalFixture();
            when(journalRepository.findById(JOURNAL_ID)).thenReturn(Optional.of(journal));

            // when & then
            assertThatThrownBy(() -> journalCommandService.deleteJournal(OTHER_MEMBER_ID, JOURNAL_ID))
                    .isInstanceOf(CustomException.class);

            assertThat(journal.isDeleted()).isFalse();
            verify(journalImageRepository, never()).findByJournalId(any());
        }
    }

    @Test
    @DisplayName("본인 일지의 사진을 삭제하면 성공한다")
    void updateJournal_deletePhoto_success() {
        // given
        Long memberId = 1L;
        Long journalId = 10L;
        Long photoId = 100L;

        Member member = mock(Member.class);
        given(member.getId()).willReturn(memberId);
        Journal journal = Journal.builder().member(member).build();
        ReflectionTestUtils.setField(journal, "id", journalId);

        JournalImage journalImage = JournalImage.builder()
                .journal(journal)
                .imageUrl("image.jpg")
                .build();

        JournalUpdateRequest.JournalPhotoUpdateRequest photoRequest =
                new JournalUpdateRequest.JournalPhotoUpdateRequest(photoId, ImageAction.DELETE, null);

        JournalUpdateRequest request = new JournalUpdateRequest(
                null, null, null, null, null,
                List.of(photoRequest),
                null
        );

        given(journalRepository.findById(journalId))
                .willReturn(Optional.of(journal));

        given(journalImageRepository.findByIdAndJournalId(photoId, journalId))
                .willReturn(Optional.of(journalImage));

        // when
        journalCommandService.updateJournal(memberId, journalId, request);

        // then
        assertTrue(journalImage.isDeleted());
    }

    @Test
    @DisplayName("다른 일지의 photoId로 삭제하면 JOURNAL_IMAGE_NOT_FOUND 예외가 발생한다")
    void updateJournal_deletePhoto_otherJournalPhoto() {
        // given
        Journal journal = createJournalFixture();

        when(journalRepository.findById(JOURNAL_ID))
                .thenReturn(Optional.of(journal));

        Long otherJournalPhotoId = 200L;

        // 다른 journalId에 속한 이미지라 조회되지 않음
        when(journalImageRepository.findByIdAndJournalId(
                otherJournalPhotoId,
                JOURNAL_ID
        ))
                .thenReturn(Optional.empty());

        JournalUpdateRequest.JournalPhotoUpdateRequest deletePhoto =
                new JournalUpdateRequest.JournalPhotoUpdateRequest(
                        otherJournalPhotoId,
                        ImageAction.DELETE,
                        null
                );

        JournalUpdateRequest request = new JournalUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                List.of(deletePhoto),
                null
        );

        // when & then
        assertThatThrownBy(() ->
                journalCommandService.updateJournal(MEMBER_ID, JOURNAL_ID, request)
        )
                .isInstanceOf(CustomException.class);

        verify(journalImageRepository, never())
                .delete(any());
    }

}
