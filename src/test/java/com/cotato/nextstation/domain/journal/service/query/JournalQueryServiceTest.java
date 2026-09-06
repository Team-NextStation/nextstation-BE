package com.cotato.nextstation.domain.journal.service.query;

import com.cotato.nextstation.domain.course.entity.CoursePlace;
import com.cotato.nextstation.domain.course.exception.CourseErrorCode;
import com.cotato.nextstation.domain.course.repository.CoursePlaceRepository;
import com.cotato.nextstation.domain.course.service.command.CourseCommandService;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.domain.journal.converter.JournalConverter;
import com.cotato.nextstation.domain.journal.dto.response.JournalDetailResponse;
import com.cotato.nextstation.domain.journal.dto.response.JournalWriteInfoResponse;
import com.cotato.nextstation.domain.journal.dto.response.MyJournalListResponse;
import com.cotato.nextstation.domain.journal.dto.response.UncompletedJournalListResponse;
import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.journal.enums.TravelDuration;
import com.cotato.nextstation.domain.journal.exception.JournalErrorCode;
import com.cotato.nextstation.domain.journal.repository.JournalImageRepository;
import com.cotato.nextstation.domain.journal.repository.JournalImageRepository.JournalImageView;
import com.cotato.nextstation.domain.journal.repository.JournalRepository;
import com.cotato.nextstation.domain.journal.repository.JournalRepository.CourseSnapshotView;
import com.cotato.nextstation.domain.journal.repository.JournalRepository.MyJournalCardView;
import com.cotato.nextstation.domain.journal.repository.JournalRepository.UncompletedCourseCardView;
import com.cotato.nextstation.domain.member.entity.Member;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.place.dto.response.PlaceInfoResponse;
import com.cotato.nextstation.domain.place.repository.PlaceReviewImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.domain.place.service.query.PlaceInfoQueryService;
import com.cotato.nextstation.domain.stamp.entity.MemberStamp;
import com.cotato.nextstation.domain.stamp.service.query.MemberStampQueryService;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import com.cotato.nextstation.domain.station.entity.LineCode;
import com.cotato.nextstation.domain.station.service.query.StationQueryService;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.util.CursorData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * JournalQueryService 테스트. 코스 상세(courseId/isMine/isLiked) 연동 및 조회수 반영 검증에 집중한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JournalQueryServiceTest {

    @Mock
    private MemberStampQueryService memberStampQueryService;
    @Mock
    private CourseQueryService courseQueryService;
    @Mock
    private CourseCommandService courseCommandService;
    @Mock
    private CoursePlaceRepository coursePlaceRepository;
    @Mock
    private PlaceInfoQueryService placeInfoQueryService;
    @Mock
    private StationQueryService stationQueryService;
    @Mock
    private JournalRepository journalRepository;
    @Mock
    private JournalImageRepository journalImageRepository;
    @Mock
    private PlaceReviewRepository placeReviewRepository;
    @Mock
    private PlaceReviewImageRepository placeReviewImageRepository;

    private JournalQueryService journalQueryService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long JOURNAL_ID = 10L;
    private static final Long MEMBER_STAMP_ID = 100L;
    private static final Long COURSE_ID = 999L;
    private static final Long STATION_ID = 6L;
    private static final Long PLACE_ID = 12L;

    private Journal journal;

    @BeforeEach
    void setUp() {
        // JournalConverter는 의존성 없는 순수 변환기라 목이 아닌 실제 인스턴스를 쓴다.
        journalQueryService = new JournalQueryService(
                memberStampQueryService, courseQueryService, courseCommandService, coursePlaceRepository,
                placeInfoQueryService, stationQueryService,
                journalRepository, journalImageRepository,
                placeReviewRepository, placeReviewImageRepository,
                new JournalConverter());

        Member owner = mock(Member.class);
        given(owner.getId()).willReturn(OWNER_ID);
        given(owner.getNickname()).willReturn("현주");

        journal = Journal.builder()
                .member(owner)
                .memberStampId(MEMBER_STAMP_ID)
                .title("보문 골목 산책")
                .traveledAt(LocalDate.of(2026, 7, 8))
                .travelDuration(TravelDuration.HALF_DAY)
                .isPublic(true)
                .build();
        ReflectionTestUtils.setField(journal, "id", JOURNAL_ID);

        given(journalRepository.findById(JOURNAL_ID)).willReturn(Optional.of(journal));
        given(memberStampQueryService.getCourseId(OWNER_ID, MEMBER_STAMP_ID)).willReturn(COURSE_ID);

        // courseQueryService.getCourseInfo() 대신 journalRepository가 course 테이블을 직접 조회한다
        // (코스가 삭제돼도 여행일지 조회는 계속돼야 해서 @SQLRestriction을 우회하는 경로를 쓴다)
        CourseSnapshotView courseSnapshot = mock(CourseSnapshotView.class);
        given(courseSnapshot.getCourseId()).willReturn(COURSE_ID);
        given(courseSnapshot.getName()).willReturn("보문에 살어리랏다");
        given(courseSnapshot.getStationId()).willReturn(STATION_ID);
        given(courseSnapshot.getViewCount()).willReturn(10);
        given(courseSnapshot.getLikeCount()).willReturn(3);
        given(journalRepository.findCourseSnapshotById(COURSE_ID)).willReturn(Optional.of(courseSnapshot));

        given(stationQueryService.getStationName(STATION_ID)).willReturn("보문역");
        given(stationQueryService.getLine(STATION_ID))
                .willReturn(new LineSummaryResponse(1L, "우이신설선", null));
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(COURSE_ID))
                .willReturn(List.of(CoursePlace.builder().courseId(COURSE_ID).placeId(PLACE_ID).orderNum(1).build()));
        given(placeInfoQueryService.getPlaceInfos(anyList())).willReturn(List.of(
                new PlaceInfoResponse(PLACE_ID, "보문숲길도서관", "설명", "CULTURE", "문화공간", null, 127.123, 37.456)));
        given(placeInfoQueryService.getTopTagNames(anyList())).willReturn(List.of());
        given(journalImageRepository.findByJournalIdOrderByIdAsc(JOURNAL_ID)).willReturn(List.of());
        given(placeReviewRepository.findByJournalId(JOURNAL_ID)).willReturn(List.of());
        given(placeReviewImageRepository.findByPlaceReviewIdIn(anyList())).willReturn(List.of());
    }

    @Nested
    @DisplayName("getWriteInfo")
    class GetWriteInfo {

        @Test
        @DisplayName("역/코스/장소 정보를 채운 작성 초기 정보를 반환한다")
        void returnsWriteInfo() {
            // when
            JournalWriteInfoResponse response = journalQueryService.getWriteInfo(OWNER_ID, MEMBER_STAMP_ID);

            // then
            assertThat(response.stationName()).isEqualTo("보문역");
            assertThat(response.courseName()).isEqualTo("보문에 살어리랏다");
            assertThat(response.places()).hasSize(1);
            assertThat(response.places().get(0).placeId()).isEqualTo(PLACE_ID);
            assertThat(response.places().get(0).placeName()).isEqualTo("보문숲길도서관");
            assertThat(response.places().get(0).orderNum()).isEqualTo(1);
        }

        @Test
        // https://github.com/IT-Cotato/13th-NextStation-BE/issues/143
        @DisplayName("완주 후 코스가 삭제됐어도(courseQueryService였다면 COURSE_NOT_FOUND) journalRepository의 " +
                "코스 스냅샷과 CoursePlaceRepository로 작성 정보가 그대로 나온다")
        void deletedCourse_stillReturnsWriteInfoUsingSnapshot() {
            // given: courseQueryService.getCourseInfo/getCoursePlaces를 실제로 호출했다면 Course의
            // @SQLRestriction 때문에 404가 났을 상황을 흉내낸다. 이 서비스가 더 이상 그 경로를 타지 않는지도 함께 검증한다.
            given(courseQueryService.getCourseInfo(COURSE_ID))
                    .willThrow(new CustomException(CourseErrorCode.COURSE_NOT_FOUND));
            given(courseQueryService.getCoursePlaces(COURSE_ID))
                    .willThrow(new CustomException(CourseErrorCode.COURSE_NOT_FOUND));

            // when
            JournalWriteInfoResponse response = journalQueryService.getWriteInfo(OWNER_ID, MEMBER_STAMP_ID);

            // then
            assertThat(response.courseName()).isEqualTo("보문에 살어리랏다");
            assertThat(response.stationName()).isEqualTo("보문역");
            verify(courseQueryService, never()).getCourseInfo(any());
            verify(courseQueryService, never()).getCoursePlaces(any());
        }

        @Test
        @DisplayName("코스 스냅샷 자체가 없으면(정상적으로는 거의 발생하지 않음) JournalErrorCode.COURSE_NOT_FOUND를 던진다")
        void courseSnapshotMissing_throwsJournalCourseNotFound() {
            // given: CourseErrorCode가 아니라 JournalErrorCode로 던지는지가 이번에 고친 지점이다
            given(journalRepository.findCourseSnapshotById(COURSE_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> journalQueryService.getWriteInfo(OWNER_ID, MEMBER_STAMP_ID))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(JournalErrorCode.COURSE_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("getJournalDetail")
    class GetJournalDetail {

        @Test
        @DisplayName("본인이 조회하면 courseId/isMine=true가 채워지고, 조회수 처리는 CourseCommandService에 위임한다")
        void ownerViews_fillsCourseIdAndIsMine() {
            // given
            given(courseQueryService.isLikedByMember(COURSE_ID, OWNER_ID)).willReturn(false);

            // when
            JournalDetailResponse response = journalQueryService.getJournalDetail(OWNER_ID, JOURNAL_ID);

            // then
            assertThat(response.writerId()).isEqualTo(OWNER_ID);
            assertThat(response.courseId()).isEqualTo(COURSE_ID);
            // https://github.com/IT-Cotato/13th-NextStation-BE/issues/171
            // journalTitle은 여행일지 자체 제목(journal.title)이어야 하고, courseName(코스 이름)과는 별개다
            assertThat(response.journalTitle()).isEqualTo("보문 골목 산책");
            assertThat(response.courseName()).isEqualTo("보문에 살어리랏다");
            assertThat(response.isMine()).isTrue();
            assertThat(response.isLiked()).isFalse();
            assertThat(response.isPublic()).isTrue();
            // 본인 조회라 실제로 증가하지 않아야 하지만, 그 판단은 CourseRepository의 SQL 조건
            // (c.memberId <> viewerMemberId)이 담당한다. 여기서는 위임(호출) 자체만 검증할 수 있고,
            // "정말 증가하지 않는지"는 이 유닛 테스트로는(courseCommandService가 목이라) 검증 대상이 아니다.
            verify(courseCommandService).increaseViewCount(COURSE_ID, OWNER_ID);
        }

        @Test
        @DisplayName("타인이 좋아요를 눌러둔 코스를 조회하면 isMine=false, isLiked=true가 채워진다")
        void otherMemberViews_fillsIsLiked() {
            // given
            given(courseQueryService.isLikedByMember(COURSE_ID, OTHER_MEMBER_ID)).willReturn(true);

            // when
            JournalDetailResponse response = journalQueryService.getJournalDetail(OTHER_MEMBER_ID, JOURNAL_ID);

            // then
            assertThat(response.isMine()).isFalse();
            assertThat(response.isLiked()).isTrue();
            assertThat(response.visitedPlaces()).hasSize(1);
            assertThat(response.visitedPlaces().get(0).xCoordinate()).isEqualTo(127.123);
            assertThat(response.visitedPlaces().get(0).yCoordinate()).isEqualTo(37.456);
            verify(courseCommandService).increaseViewCount(COURSE_ID, OTHER_MEMBER_ID);
        }

        @Test
        @DisplayName("타인 조회로 조회수 증가에 성공하면 CourseCommandService가 반환한 최신 값을 응답에 담는다")
        void otherMemberViews_responseUsesReturnedViewCount() {
            // given: setUp의 courseInfo는 viewCount=10이지만, 증가 호출이 반환하는 11을 응답에 써야 한다.
            // (같은 readOnly 트랜잭션에서 courseInfo를 다시 조회하면 REPEATABLE READ 스냅샷 때문에
            //  여전히 10이 보이므로, 재조회가 아니라 반환값을 받아써야 증가분이 응답에 반영된다.)
            given(courseCommandService.increaseViewCount(COURSE_ID, OTHER_MEMBER_ID)).willReturn(11);
            given(courseQueryService.isLikedByMember(COURSE_ID, OTHER_MEMBER_ID)).willReturn(false);

            // when
            JournalDetailResponse response = journalQueryService.getJournalDetail(OTHER_MEMBER_ID, JOURNAL_ID);

            // then
            assertThat(response.viewCount()).isEqualTo(11);
        }

        @Test
        @DisplayName("조회수 증가가 null을 반환하면(본인 조회 또는 실패) 4번에서 조회해 둔 값으로 대체한다")
        void increaseViewCountReturnsNull_fallsBackToOriginalCourseInfo() {
            // given: setUp의 courseInfo는 viewCount=10. increaseViewCount가 실패(또는 본인 조회로 no-op)해
            // null을 반환하는 상황을 명시적으로 스텁한다 (모의 객체의 Integer 반환 기본값은 null이 아니라
            // 0이라 명시하지 않으면 이 케이스를 재현하지 못한다).
            given(courseCommandService.increaseViewCount(COURSE_ID, OTHER_MEMBER_ID)).willReturn(null);
            given(courseQueryService.isLikedByMember(COURSE_ID, OTHER_MEMBER_ID)).willReturn(false);

            // when
            JournalDetailResponse response = journalQueryService.getJournalDetail(OTHER_MEMBER_ID, JOURNAL_ID);

            // then
            assertThat(response.viewCount()).isEqualTo(10);
        }

        @Test
        // https://github.com/IT-Cotato/13th-NextStation-BE/issues/143
        @DisplayName("완주 후 코스가 삭제됐어도(courseQueryService였다면 COURSE_NOT_FOUND) journalRepository의 " +
                "코스 스냅샷으로 상세 조회가 그대로 성공한다")
        void deletedCourse_stillReturnsDetailUsingSnapshot() {
            // given: courseQueryService.getCourseInfo/getCoursePlaces를 실제로 호출했다면 Course의
            // @SQLRestriction 때문에 404가 났을 상황을 흉내낸다. 이 서비스가 더 이상 그 경로를 타지 않는지도 함께 검증한다.
            given(courseQueryService.getCourseInfo(COURSE_ID))
                    .willThrow(new CustomException(CourseErrorCode.COURSE_NOT_FOUND));
            given(courseQueryService.getCoursePlaces(COURSE_ID))
                    .willThrow(new CustomException(CourseErrorCode.COURSE_NOT_FOUND));
            given(courseQueryService.isLikedByMember(COURSE_ID, OWNER_ID)).willReturn(false);

            // when
            JournalDetailResponse response = journalQueryService.getJournalDetail(OWNER_ID, JOURNAL_ID);

            // then
            assertThat(response.courseName()).isEqualTo("보문에 살어리랏다");
            assertThat(response.stationName()).isEqualTo("보문역");
            verify(courseQueryService, never()).getCourseInfo(any());
            verify(courseQueryService, never()).getCoursePlaces(any());
        }

        @Test
        @DisplayName("비로그인 조회도 조회수 반영 호출은 그대로 나간다 (본인 제외 판단은 CourseCommandService 몫)")
        void anonymousViews_stillCallsIncreaseViewCount() {
            // given
            given(courseQueryService.isLikedByMember(COURSE_ID, null)).willReturn(false);

            // when
            JournalDetailResponse response = journalQueryService.getJournalDetail(null, JOURNAL_ID);

            // then
            assertThat(response.writerId()).isEqualTo(OWNER_ID);
            assertThat(response.isMine()).isFalse();
            assertThat(response.isLiked()).isFalse();
            verify(courseCommandService).increaseViewCount(COURSE_ID, null);
        }

        @Test
        @DisplayName("타인이 비공개 일지를 조회하면 JOURNAL_NOT_FOUND를 던지고 조회수는 증가하지 않는다")
        void otherMemberViewsPrivateJournal_throwsNotFoundAndNeverIncreasesViewCount() {
            // given: setUp의 journal은 공개 상태라, 이 테스트만 비공개로 재정의한다
            Journal privateJournal = Journal.builder()
                    .member(journal.getMember())
                    .memberStampId(MEMBER_STAMP_ID)
                    .title("보문 골목 산책")
                    .traveledAt(LocalDate.of(2026, 7, 8))
                    .travelDuration(TravelDuration.HALF_DAY)
                    .isPublic(false)
                    .build();
            ReflectionTestUtils.setField(privateJournal, "id", JOURNAL_ID);
            given(journalRepository.findById(JOURNAL_ID)).willReturn(Optional.of(privateJournal));

            // when & then: 권한 검증에서 막혀야 하고, 그 뒤에 있는 조회수 증가 호출까지 가면 안 된다
            assertThatThrownBy(() -> journalQueryService.getJournalDetail(OTHER_MEMBER_ID, JOURNAL_ID))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(JournalErrorCode.JOURNAL_NOT_FOUND.getMessage());

            verify(courseCommandService, never()).increaseViewCount(anyLong(), any());
        }

        @Test
        @DisplayName("비로그인이 비공개 일지를 조회해도 JOURNAL_NOT_FOUND를 던진다")
        void anonymousViewsPrivateJournal_throwsNotFound() {
            Journal privateJournal = Journal.builder()
                    .member(journal.getMember())
                    .memberStampId(MEMBER_STAMP_ID)
                    .title("보문 골목 산책")
                    .traveledAt(LocalDate.of(2026, 7, 8))
                    .travelDuration(TravelDuration.HALF_DAY)
                    .isPublic(false)
                    .build();
            ReflectionTestUtils.setField(privateJournal, "id", JOURNAL_ID);
            given(journalRepository.findById(JOURNAL_ID)).willReturn(Optional.of(privateJournal));

            assertThatThrownBy(() -> journalQueryService.getJournalDetail(null, JOURNAL_ID))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(JournalErrorCode.JOURNAL_NOT_FOUND.getMessage());

            verify(courseCommandService, never()).increaseViewCount(anyLong(), any());
        }

        @Test
        @DisplayName("타인이 탈퇴한 작성자의 공개 일지를 조회하면 JOURNAL_NOT_FOUND를 던진다")
        void otherMemberViewsWithdrawnAuthorJournal_throwsNotFoundAndNeverIncreasesViewCount() {
            // given: 탈퇴는 soft delete라 journal 행과 작성자(member) 행은 남아 있지만, 재가입 시
            // 과거 콘텐츠가 다시 노출되지 않도록 타인 조회는 막는다. isPublic 여부와 무관하게 막혀야 한다.
            given(journal.getMember().getStatus()).willReturn(MemberStatus.WITHDRAWN);

            // when & then
            assertThatThrownBy(() -> journalQueryService.getJournalDetail(OTHER_MEMBER_ID, JOURNAL_ID))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(JournalErrorCode.JOURNAL_NOT_FOUND.getMessage());

            verify(courseCommandService, never()).increaseViewCount(anyLong(), any());
        }

        @Test
        @DisplayName("본인은 자신이 탈퇴 처리된 상태여도(예: 탈퇴 유예 기간 중 재로그인) 자기 일지를 그대로 조회할 수 있다")
        void ownerViewsOwnJournal_evenIfWithdrawn() {
            // given
            given(journal.getMember().getStatus()).willReturn(MemberStatus.WITHDRAWN);
            given(courseQueryService.isLikedByMember(COURSE_ID, OWNER_ID)).willReturn(false);

            // when & then: 본인 조회는 status를 보지 않으므로 예외 없이 성공해야 한다
            assertThatCode(() -> journalQueryService.getJournalDetail(OWNER_ID, JOURNAL_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("본인이 비공개 일지를 조회하면 정상적으로 조회되고 isPublic=false가 채워진다")
        void ownerViewsPrivateJournal_fillsIsPublicFalse() {
            // given: setUp의 journal은 공개 상태라, 이 테스트만 비공개로 재정의한다
            Journal privateJournal = Journal.builder()
                    .member(journal.getMember())
                    .memberStampId(MEMBER_STAMP_ID)
                    .title("보문 골목 산책")
                    .traveledAt(LocalDate.of(2026, 7, 8))
                    .travelDuration(TravelDuration.HALF_DAY)
                    .isPublic(false)
                    .build();
            ReflectionTestUtils.setField(privateJournal, "id", JOURNAL_ID);
            given(journalRepository.findById(JOURNAL_ID)).willReturn(Optional.of(privateJournal));
            given(courseQueryService.isLikedByMember(COURSE_ID, OWNER_ID)).willReturn(false);

            // when
            JournalDetailResponse response = journalQueryService.getJournalDetail(OWNER_ID, JOURNAL_ID);

            // then: 본인 조회라 비공개여도 막히지 않고, 공개 범위가 그대로 응답에 반영된다
            assertThat(response.isMine()).isTrue();
            assertThat(response.isPublic()).isFalse();
        }
    }

    @Nested
    @DisplayName("getUncompletedJournals")
    class GetUncompletedJournals {

        private static final Long COURSE_ID_1 = 501L;
        private static final Long COURSE_ID_2 = 502L; // 삭제된 코스를 흉내낸다

        @Test
        // https://github.com/IT-Cotato/13th-NextStation-BE/issues/143
        @DisplayName("미작성 스탬프 중 하나가 참조하는 코스가 삭제됐어도(courseQueryService였다면 그 스탬프에서 " +
                "404) 목록 조회 전체가 성공하고 삭제된 코스의 이름·역·호선도 그대로 나온다")
        void oneCourseDeleted_stillReturnsFullList() {
            // given
            MemberStamp stamp1 = mock(MemberStamp.class);
            given(stamp1.getId()).willReturn(201L);
            given(stamp1.getCourseId()).willReturn(COURSE_ID_1);
            given(stamp1.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 1, 10, 0));

            MemberStamp stamp2 = mock(MemberStamp.class);
            given(stamp2.getId()).willReturn(202L);
            given(stamp2.getCourseId()).willReturn(COURSE_ID_2);
            given(stamp2.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 2, 10, 0));

            given(journalRepository.findCompletedMemberStampIdsByMemberId(OWNER_ID)).willReturn(Set.of());
            given(memberStampQueryService.getUncompletedStamps(OWNER_ID, Set.of()))
                    .willReturn(List.of(stamp1, stamp2));

            // course→station→line 네이티브 조인은 코스가 삭제됐어도(is_deleted=true) 그대로 값을 돌려준다.
            // 대표 호선이 없는 역(card2)도 함께 검증한다(LEFT JOIN이라 line 컬럼이 전부 null).
            UncompletedCourseCardView card1 = mock(UncompletedCourseCardView.class);
            given(card1.getCourseId()).willReturn(COURSE_ID_1);
            given(card1.getName()).willReturn("보문에 살어리랏다");
            given(card1.getStationName()).willReturn("보문역");
            given(card1.getLineId()).willReturn(6L);
            given(card1.getLineName()).willReturn("6호선");
            given(card1.getLineCode()).willReturn(LineCode.LINE_6);

            UncompletedCourseCardView card2 = mock(UncompletedCourseCardView.class);
            given(card2.getCourseId()).willReturn(COURSE_ID_2);
            given(card2.getName()).willReturn("삭제되기 전 코스 이름");
            given(card2.getStationName()).willReturn("한성대입구역");
            given(card2.getLineId()).willReturn(null);

            given(journalRepository.findUncompletedCourseCardsByIds(List.of(COURSE_ID_1, COURSE_ID_2)))
                    .willReturn(List.of(card1, card2));

            given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(COURSE_ID_1)).willReturn(List.of());
            given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(COURSE_ID_2)).willReturn(List.of());
            given(placeInfoQueryService.getTopTagNames(anyList())).willReturn(List.of());

            // 실제로 courseQueryService.getCourseInfo(COURSE_ID_2)를 호출했다면 삭제된 코스라 404가
            // 났을 상황을 흉내낸다. 이 서비스가 더 이상 그 경로를 타지 않는지도 함께 검증한다.
            given(courseQueryService.getCourseInfo(COURSE_ID_2))
                    .willThrow(new CustomException(CourseErrorCode.COURSE_NOT_FOUND));

            // when
            UncompletedJournalListResponse response = journalQueryService.getUncompletedJournals(OWNER_ID);

            // then
            assertThat(response.totalCount()).isEqualTo(2);
            assertThat(response.courses())
                    .extracting(UncompletedJournalListResponse.UncompletedCourseResponse::courseName)
                    .containsExactlyInAnyOrder("보문에 살어리랏다", "삭제되기 전 코스 이름");
            assertThat(response.courses())
                    .extracting(UncompletedJournalListResponse.UncompletedCourseResponse::stationName)
                    .containsExactlyInAnyOrder("보문역", "한성대입구역");
            assertThat(response.courses())
                    .filteredOn(course -> course.courseName().equals("보문에 살어리랏다"))
                    .extracting(UncompletedJournalListResponse.UncompletedCourseResponse::line)
                    .containsExactly(new LineSummaryResponse(6L, "6호선", LineCode.LINE_6));
            assertThat(response.courses())
                    .filteredOn(course -> course.courseName().equals("삭제되기 전 코스 이름"))
                    .extracting(UncompletedJournalListResponse.UncompletedCourseResponse::line)
                    .containsExactly((LineSummaryResponse) null);
            verify(courseQueryService, never()).getCourseInfo(any());
            verify(courseQueryService, never()).getCoursePlaces(any());
        }

        @Test
        @DisplayName("배치 조회 결과에 courseId 하나가 통째로 빠져도(course/station 하드 삭제 등) " +
                "NPE 없이 그 스탬프만 제외하고 나머지 목록은 정상 반환한다")
        void courseCardMissingFromBatchResult_excludesThatStampOnly() {
            // given
            MemberStamp stamp1 = mock(MemberStamp.class);
            given(stamp1.getId()).willReturn(301L);
            given(stamp1.getCourseId()).willReturn(COURSE_ID_1);
            given(stamp1.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 1, 10, 0));

            // course나 station이 하드 삭제되면 INNER JOIN 때문에 이 courseId는 배치 조회
            // 결과에서 아예 빠진다 (course_place.place_id가 재시딩으로 끊기는 것과 같은 종류의
            // 참조 무결성 문제).
            MemberStamp stamp2 = mock(MemberStamp.class);
            given(stamp2.getId()).willReturn(302L);
            given(stamp2.getCourseId()).willReturn(COURSE_ID_2);
            given(stamp2.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 2, 10, 0));

            given(journalRepository.findCompletedMemberStampIdsByMemberId(OWNER_ID)).willReturn(Set.of());
            given(memberStampQueryService.getUncompletedStamps(OWNER_ID, Set.of()))
                    .willReturn(List.of(stamp1, stamp2));

            UncompletedCourseCardView card1 = mock(UncompletedCourseCardView.class);
            given(card1.getCourseId()).willReturn(COURSE_ID_1);
            given(card1.getName()).willReturn("보문에 살어리랏다");
            given(card1.getStationName()).willReturn("보문역");
            given(card1.getLineId()).willReturn(6L);
            given(card1.getLineName()).willReturn("6호선");
            given(card1.getLineCode()).willReturn(LineCode.LINE_6);

            // COURSE_ID_2의 카드는 응답에 없다
            given(journalRepository.findUncompletedCourseCardsByIds(List.of(COURSE_ID_1, COURSE_ID_2)))
                    .willReturn(List.of(card1));

            given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(COURSE_ID_1)).willReturn(List.of());
            given(placeInfoQueryService.getTopTagNames(anyList())).willReturn(List.of());

            // when
            UncompletedJournalListResponse response = journalQueryService.getUncompletedJournals(OWNER_ID);

            // then
            assertThat(response.totalCount()).isEqualTo(1);
            assertThat(response.courses())
                    .extracting(UncompletedJournalListResponse.UncompletedCourseResponse::memberStampId)
                    .containsExactly(301L);
            assertThat(response.courses())
                    .extracting(UncompletedJournalListResponse.UncompletedCourseResponse::courseName)
                    .containsExactly("보문에 살어리랏다");
            // 빠진 courseId의 CoursePlace/태그는 조회할 필요가 없다
            verify(coursePlaceRepository, never()).findByCourseIdOrderByOrderNumAsc(COURSE_ID_2);
        }

        @Test
        @DisplayName("미작성 스탬프가 없으면 빈 목록을 반환하고 코스 카드 조회는 나가지 않는다")
        void noUncompletedStamps_returnsEmptyList() {
            // given
            given(journalRepository.findCompletedMemberStampIdsByMemberId(OWNER_ID)).willReturn(Set.of());
            given(memberStampQueryService.getUncompletedStamps(OWNER_ID, Set.of())).willReturn(List.of());

            // when
            UncompletedJournalListResponse response = journalQueryService.getUncompletedJournals(OWNER_ID);

            // then
            assertThat(response.totalCount()).isEqualTo(0);
            assertThat(response.courses()).isEmpty();
            verify(journalRepository, never()).findUncompletedCourseCardsByIds(any());
        }
    }

    @Nested
    @DisplayName("getMyJournals")
    class GetMyJournals {

        @Test
        @DisplayName("일지가 없으면 빈 목록을 반환하고 사진 조회는 나가지 않는다")
        void noJournals_returnsEmptyListWithoutImageQuery() {
            // given
            given(journalRepository.findMyJournalCards(eq(OWNER_ID), any())).willReturn(List.of());

            // when
            MyJournalListResponse response = journalQueryService.getMyJournals(OWNER_ID, null, null);

            // then
            assertThat(response.journals()).isEmpty();
            assertThat(response.hasNext()).isFalse();
            assertThat(response.nextCursor()).isNull();
            verify(journalImageRepository, never()).findImagesByJournalIds(anyList());
        }

        @Test
        @DisplayName("대표 호선이 있는 역이면 line을 채우고, 일지별 첫 사진을 썸네일로 담는다")
        void journalsWithDrawLine_fillsLineAndThumbnail() {
            // given
            MyJournalCardView card = mock(MyJournalCardView.class);
            given(card.getJournalId()).willReturn(JOURNAL_ID);
            given(card.getTitle()).willReturn("보문 골목 산책");
            given(card.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 8, 12, 0));
            given(card.getLikeCount()).willReturn(54);
            given(card.getStationName()).willReturn("보문역");
            given(card.getLineId()).willReturn(1L);
            given(card.getLineName()).willReturn("6호선");
            given(card.getLineCode()).willReturn(LineCode.LINE_6);
            given(journalRepository.findMyJournalCards(eq(OWNER_ID), any())).willReturn(List.of(card));

            JournalImageView imageView = mock(JournalImageView.class);
            given(imageView.getJournalId()).willReturn(JOURNAL_ID);
            given(imageView.getImageUrl()).willReturn("https://s3.../journal/10/uuid1.jpg");
            given(journalImageRepository.findImagesByJournalIds(List.of(JOURNAL_ID))).willReturn(List.of(imageView));

            // when
            MyJournalListResponse response = journalQueryService.getMyJournals(OWNER_ID, null, null);

            // then
            assertThat(response.journals()).hasSize(1);
            assertThat(response.journals().get(0).journalId()).isEqualTo(JOURNAL_ID);
            assertThat(response.journals().get(0).title()).isEqualTo("보문 골목 산책");
            assertThat(response.journals().get(0).stationName()).isEqualTo("보문역");
            assertThat(response.journals().get(0).likeCount()).isEqualTo(54);
            assertThat(response.journals().get(0).thumbnailUrl()).isEqualTo("https://s3.../journal/10/uuid1.jpg");
            assertThat(response.journals().get(0).line()).isEqualTo(new LineSummaryResponse(1L, "6호선", LineCode.LINE_6));
            assertThat(response.hasNext()).isFalse();
            assertThat(response.nextCursor()).isNull();
        }

        @Test
        @DisplayName("대표 호선이 없는 역이면 line은 null이고, 사진이 없으면 썸네일도 null이다")
        void journalWithoutDrawLineOrImage_leavesLineAndThumbnailNull() {
            // given
            MyJournalCardView card = mock(MyJournalCardView.class);
            given(card.getJournalId()).willReturn(JOURNAL_ID);
            given(card.getTitle()).willReturn("혼자 걷는 성신여대");
            given(card.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 8, 12, 0));
            given(card.getLikeCount()).willReturn(0);
            given(card.getStationName()).willReturn("성신여대입구역");
            given(card.getLineId()).willReturn(null);
            given(journalRepository.findMyJournalCards(eq(OWNER_ID), any())).willReturn(List.of(card));
            given(journalImageRepository.findImagesByJournalIds(List.of(JOURNAL_ID))).willReturn(List.of());

            // when
            MyJournalListResponse response = journalQueryService.getMyJournals(OWNER_ID, null, null);

            // then
            assertThat(response.journals().get(0).line()).isNull();
            assertThat(response.journals().get(0).thumbnailUrl()).isNull();
        }

        @Test
        @DisplayName("조회 결과가 요청 size보다 많으면 hasNext=true이고 마지막 항목 기준 nextCursor가 생성된다")
        void moreThanPageSize_hasNextTrueAndNextCursorGenerated() {
            // given: size=1 요청 시 서비스는 2개(size+1)를 조회해 다음 페이지 존재 여부를 판단한다
            MyJournalCardView card1 = mock(MyJournalCardView.class);
            given(card1.getJournalId()).willReturn(10L);
            given(card1.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 8, 12, 0));
            given(card1.getTitle()).willReturn("보문 골목 산책");
            given(card1.getStationName()).willReturn("보문역");

            MyJournalCardView card2 = mock(MyJournalCardView.class);
            given(card2.getJournalId()).willReturn(9L);
            given(card2.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 7, 12, 0));
            given(card2.getTitle()).willReturn("혼자 걷는 성신여대");
            given(card2.getStationName()).willReturn("성신여대입구역");

            given(journalRepository.findMyJournalCards(eq(OWNER_ID), any())).willReturn(List.of(card1, card2));
            given(journalImageRepository.findImagesByJournalIds(List.of(10L))).willReturn(List.of());

            // when
            MyJournalListResponse response = journalQueryService.getMyJournals(OWNER_ID, null, 1);

            // then: 2번째 카드는 hasNext 판단용으로만 쓰이고 응답에는 담기지 않는다
            assertThat(response.journals()).hasSize(1);
            assertThat(response.journals().get(0).journalId()).isEqualTo(10L);
            assertThat(response.hasNext()).isTrue();
            assertThat(response.nextCursor()).isNotNull();
        }

        @Test
        @DisplayName("cursor가 주어지면 findMyJournalCardsAfterCursor로 다음 페이지를 조회한다")
        void withCursor_callsFindAfterCursor() {
            // given
            LocalDateTime cursorCreatedAt = LocalDateTime.of(2026, 7, 8, 12, 0);
            CursorData cursorData = new CursorData(10L, null, cursorCreatedAt);
            String cursor = cursorData.encode();

            given(journalRepository.findMyJournalCardsAfterCursor(eq(OWNER_ID), eq(cursorCreatedAt), eq(10L), any()))
                    .willReturn(List.of());

            // when
            journalQueryService.getMyJournals(OWNER_ID, cursor, null);

            // then
            verify(journalRepository).findMyJournalCardsAfterCursor(eq(OWNER_ID), eq(cursorCreatedAt), eq(10L), any());
            verify(journalRepository, never()).findMyJournalCards(any(), any());
        }

        @Test
        @DisplayName("size가 1~50 범위를 벗어나면 INVALID_PAGE_SIZE 예외를 던진다")
        void invalidSize_throwsInvalidPageSize() {
            // when & then
            assertThatThrownBy(() -> journalQueryService.getMyJournals(OWNER_ID, null, 0))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(GlobalErrorCode.INVALID_PAGE_SIZE.getMessage());

            assertThatThrownBy(() -> journalQueryService.getMyJournals(OWNER_ID, null, 51))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(GlobalErrorCode.INVALID_PAGE_SIZE.getMessage());

            verify(journalRepository, never()).findMyJournalCards(any(), any());
        }

        @Test
        @DisplayName("커서에 정렬 기준값(longValue)이 채워져 있으면 INVALID_CURSOR 예외를 던진다")
        void cursorWithLongValue_throwsInvalidCursor() {
            // given: 이 목록은 시간순 정렬만 지원하므로, longValue가 채워진 커서는
            // (courseLikeCount 등 다른 정렬 기준의) 잘못된 커서로 취급해야 한다
            CursorData cursorData = new CursorData(10L, 5L, LocalDateTime.now());
            String cursor = cursorData.encode();

            // when & then
            assertThatThrownBy(() -> journalQueryService.getMyJournals(OWNER_ID, cursor, null))
                    .isInstanceOf(CustomException.class)
                    .hasMessageContaining(GlobalErrorCode.INVALID_CURSOR.getMessage());
        }
    }

    @Nested
    @DisplayName("getTravelDuration")
    class GetTravelDuration {

        @Test
        @DisplayName("journalId가 null이면 findById를 호출하지 않고 null을 반환한다")
        void nullJournalId_returnsNullWithoutQuery() {
            // when & then
            assertThatCode(() -> {
                TravelDuration result = journalQueryService.getTravelDuration(null);
                assertThat(result).isNull();
            }).doesNotThrowAnyException();

            verify(journalRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("존재하는 journalId면 해당 일지의 travelDuration을 반환한다")
        void existingJournalId_returnsTravelDuration() {
            // when
            TravelDuration result = journalQueryService.getTravelDuration(JOURNAL_ID);

            // then
            assertThat(result).isEqualTo(TravelDuration.HALF_DAY);
        }
    }
}
