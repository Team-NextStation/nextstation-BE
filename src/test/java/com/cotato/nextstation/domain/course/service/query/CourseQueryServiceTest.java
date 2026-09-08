package com.cotato.nextstation.domain.course.service.query;

import com.cotato.nextstation.domain.course.converter.CourseConverter;
import com.cotato.nextstation.domain.course.dto.request.ExploreCourseCondition;
import com.cotato.nextstation.domain.course.dto.response.CourseInfoResponse;
import com.cotato.nextstation.domain.course.dto.response.CoursePlaceInfoResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreCourseListResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreCourseResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreLineResponse;
import com.cotato.nextstation.domain.course.dto.response.MyCourseDetailResponse;
import com.cotato.nextstation.domain.course.dto.response.CoursePlaceDetailResponse;
import com.cotato.nextstation.domain.course.dto.response.PopularCourseResponse;
import com.cotato.nextstation.domain.course.entity.Course;
import com.cotato.nextstation.domain.course.entity.CoursePlace;
import com.cotato.nextstation.domain.course.entity.CourseSort;
import com.cotato.nextstation.domain.course.exception.CourseErrorCode;
import com.cotato.nextstation.domain.course.dto.response.PlaceCourseResponse;
import com.cotato.nextstation.domain.course.repository.CoursePlaceRepository;
import com.cotato.nextstation.domain.course.repository.CourseRepository;
import com.cotato.nextstation.domain.course.repository.CourseRepository.LineView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.ExploreCourseView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.CourseDetailView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.MyCourseView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.MemberCourseCardView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.PlaceCourseView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.PopularCourseView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.StationView;
import com.cotato.nextstation.domain.course.repository.CourseLikeRepository;
import com.cotato.nextstation.domain.course.repository.CourseLikeRepository.LikedCourseView;
import com.cotato.nextstation.domain.journal.dto.response.JournalCardInfoResponse;
import com.cotato.nextstation.domain.journal.service.query.JournalCardQueryService;
import com.cotato.nextstation.domain.member.exception.MemberErrorCode;
import com.cotato.nextstation.domain.member.service.query.MemberExistenceQueryService;
import com.cotato.nextstation.domain.course.dto.response.MemberCourseListResponse;
import com.cotato.nextstation.domain.place.dto.response.PlaceInfoResponse;
import com.cotato.nextstation.domain.place.dto.response.HistoricalPlaceInfoResponse;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.service.query.PlaceInfoQueryService;
import com.cotato.nextstation.domain.stamp.service.query.MemberStampQueryService;
import com.cotato.nextstation.domain.station.entity.LineCode;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.util.CursorData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseQueryServiceTest {

    @InjectMocks
    private CourseQueryService courseQueryService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CoursePlaceRepository coursePlaceRepository;

    @Mock
    private CourseLikeRepository courseLikeRepository;

    @Mock
    private PlaceInfoQueryService placeInfoQueryService;

    @Mock
    private MemberStampQueryService memberStampQueryService;

    @Mock
    private JournalCardQueryService journalCardQueryService;

    @Mock
    private MemberExistenceQueryService memberExistenceQueryService;

    @Mock
    private CourseConverter courseConverter;

    private Course course(String name) {
        return Course.builder().memberId(1L).stationId(100L).name(name).build();
    }

    // ---------- 장소를 포함한 코스 ----------

    // 프로젝션은 인터페이스라 mock으로 만든다. 스터빙이 들어 있어 given(...) 밖에서 미리 생성한다.
    private PlaceCourseView placeCourseView(Long courseId) {
        PlaceCourseView view = mock(PlaceCourseView.class);
        lenient().when(view.getCourseId()).thenReturn(courseId);
        return view;
    }

    private CoursePlace coursePlace(Long courseId, Long placeId, int orderNum) {
        return CoursePlace.builder().courseId(courseId).placeId(placeId).orderNum(orderNum).build();
    }

    @Test
    @DisplayName("장소를 포함한 코스는 인기순 상위 6개만 조회한다")
    void getCoursesByPlace_limitsToSix() {
        // given
        given(courseRepository.findPopularPublicCoursesByPlaceId(eq(1L), any(Pageable.class)))
                .willReturn(List.of());

        // when
        List<PlaceCourseResponse> result = courseQueryService.getCoursesByPlace(1L);

        // then: 더보기가 없는 화면이라 6개 고정으로 요청한다
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(courseRepository).findPopularPublicCoursesByPlaceId(eq(1L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 6));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("코스가 없으면 장소 조회를 하지 않고 빈 목록을 반환한다")
    void getCoursesByPlace_noCourses() {
        // given
        given(courseRepository.findPopularPublicCoursesByPlaceId(eq(1L), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getCoursesByPlace(1L);

        // then: 빈 id 목록으로 장소·태그를 조회하는 낭비를 막는다
        verify(coursePlaceRepository, never()).findByCourseIdInOrderByCourseIdAscOrderNumAsc(any());
        verify(placeInfoQueryService, never()).getTopTagNames(any());
    }

    @Test
    @DisplayName("코스별 장소 수와 대표 태그를 함께 내려준다")
    void getCoursesByPlace_placeCountAndTags() {
        // given: 10번 코스는 장소 3개, 20번 코스는 장소 2개
        PlaceCourseView view10 = placeCourseView(10L);
        PlaceCourseView view20 = placeCourseView(20L);
        given(courseRepository.findPopularPublicCoursesByPlaceId(eq(1L), any(Pageable.class)))
                .willReturn(List.of(view10, view20));
        given(coursePlaceRepository.findByCourseIdInOrderByCourseIdAscOrderNumAsc(any()))
                .willReturn(List.of(
                        coursePlace(10L, 100L, 1), coursePlace(10L, 101L, 2), coursePlace(10L, 102L, 3),
                        coursePlace(20L, 200L, 1), coursePlace(20L, 201L, 2)));
        given(placeInfoQueryService.getHistoricalPlaceInfos(any())).willReturn(List.of());
        given(placeInfoQueryService.getTopTagNames(List.of(100L, 101L, 102L)))
                .willReturn(List.of("자연과함께", "사진찍기좋은", "가성비"));
        given(placeInfoQueryService.getTopTagNames(List.of(200L, 201L)))
                .willReturn(List.of("실내위주"));

        // when
        courseQueryService.getCoursesByPlace(1L);

        // then
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<List<String>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        verify(courseConverter, times(2))
                .toPlaceCourseResponse(any(), countCaptor.capture(), tagsCaptor.capture(), any(), any());
        // 노출 순서를 섞으므로 두 코스가 어떤 순서로 들어와도 되도록 쌍으로 확인한다
        assertThat(countCaptor.getAllValues()).containsExactlyInAnyOrder(3, 2);
        // 카드에는 태그를 2개까지만 노출한다
        assertThat(tagsCaptor.getAllValues())
                .containsExactlyInAnyOrder(List.of("자연과함께", "사진찍기좋은"), List.of("실내위주"));
    }

    @Test
    @DisplayName("카드 배경은 코스의 첫 번째 장소 이미지를 쓴다")
    void getCoursesByPlace_coverImage() {
        // given: 10번 코스의 첫 장소는 order_num이 가장 작은 100번
        PlaceCourseView view10 = placeCourseView(10L);
        given(courseRepository.findPopularPublicCoursesByPlaceId(eq(1L), any(Pageable.class)))
                .willReturn(List.of(view10));
        given(coursePlaceRepository.findByCourseIdInOrderByCourseIdAscOrderNumAsc(any()))
                .willReturn(List.of(coursePlace(10L, 100L, 1), coursePlace(10L, 101L, 2)));
        given(placeInfoQueryService.getHistoricalPlaceInfos(List.of(100L))).willReturn(List.of(
                new HistoricalPlaceInfoResponse(
                        100L, "보문골한옥집", "설명", "FOOD", "식당", "cover.jpg", 127.0, 37.5, PlaceStatus.DELETED)));
        given(placeInfoQueryService.getTopTagNames(any())).willReturn(List.of());

        // when
        courseQueryService.getCoursesByPlace(1L);

        // then: 두 번째 장소가 아니라 첫 번째 장소의 이미지를 조회해 넘긴다
        ArgumentCaptor<String> imageCaptor = ArgumentCaptor.forClass(String.class);
        verify(courseConverter).toPlaceCourseResponse(any(), anyInt(), any(), imageCaptor.capture(), any());
        assertThat(imageCaptor.getValue()).isEqualTo("cover.jpg");
    }

    @Test
    @DisplayName("장소 이미지가 없으면 배경 이미지는 null로 내려간다")
    void getCoursesByPlace_noCoverImage() {
        // given: 이미지가 아직 없는 장소 (장소 이미지·카테고리 기본 이미지 모두 없음)
        PlaceCourseView view10 = placeCourseView(10L);
        given(courseRepository.findPopularPublicCoursesByPlaceId(eq(1L), any(Pageable.class)))
                .willReturn(List.of(view10));
        given(coursePlaceRepository.findByCourseIdInOrderByCourseIdAscOrderNumAsc(any()))
                .willReturn(List.of(coursePlace(10L, 100L, 1)));
        given(placeInfoQueryService.getHistoricalPlaceInfos(List.of(100L))).willReturn(List.of(
                new HistoricalPlaceInfoResponse(
                        100L, "보문골한옥집", "설명", "FOOD", "식당", null, 127.0, 37.5, PlaceStatus.APPROVED)));
        given(placeInfoQueryService.getTopTagNames(any())).willReturn(List.of());

        // when
        courseQueryService.getCoursesByPlace(1L);

        // then
        ArgumentCaptor<String> imageCaptor = ArgumentCaptor.forClass(String.class);
        verify(courseConverter).toPlaceCourseResponse(any(), anyInt(), any(), imageCaptor.capture(), any());
        assertThat(imageCaptor.getValue()).isNull();
    }

    @Test
    @DisplayName("코스 정보를 조회하면 CourseInfoResponse를 반환한다")
    void getCourseInfo_success() {
        // given
        Course course = course("성수 코스");
        CourseInfoResponse response = new CourseInfoResponse(
                1L, "성수 코스", 1L, 100L, null, 0, 0, course.getCreatedAt());
        given(courseRepository.findById(1L)).willReturn(Optional.of(course));
        given(courseConverter.toInfoResponse(course)).willReturn(response);

        // when
        CourseInfoResponse result = courseQueryService.getCourseInfo(1L);

        // then
        assertThat(result).isEqualTo(response);
    }

    @Test
    @DisplayName("없는 코스의 정보를 조회하면 예외가 발생한다")
    void getCourseInfo_notFound() {
        // given
        given(courseRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> courseQueryService.getCourseInfo(1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("코스의 장소 목록을 순서대로 조회한다")
    void getCoursePlaces_success() {
        // given
        Course course = course("성수 코스");
        List<CoursePlace> coursePlaces = List.of(
                CoursePlace.builder().courseId(1L).placeId(10L).orderNum(1).build(),
                CoursePlace.builder().courseId(1L).placeId(20L).orderNum(2).build()
        );
        List<CoursePlaceInfoResponse> responses = List.of(
                new CoursePlaceInfoResponse(10L, 1), new CoursePlaceInfoResponse(20L, 2));
        given(courseRepository.findById(1L)).willReturn(Optional.of(course));
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(1L)).willReturn(coursePlaces);
        given(courseConverter.toPlaceInfoResponses(coursePlaces)).willReturn(responses);

        // when
        List<CoursePlaceInfoResponse> result = courseQueryService.getCoursePlaces(1L);

        // then
        assertThat(result).isEqualTo(responses);
    }

    @Test
    @DisplayName("없는 코스의 장소 목록을 조회하면 예외가 발생한다")
    void getCoursePlaces_notFound() {
        // given
        given(courseRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> courseQueryService.getCoursePlaces(1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_NOT_FOUND.getMessage());
    }

    // ---------- 저장 탭 - 좋아요한 코스 목록 ----------

    // 프로젝션은 인터페이스라 mock으로 만든다. 스터빙이 들어 있으므로 given(...) 밖에서 미리 생성한다.
    private LikedCourseView likedView(Long likeId, Long courseId, LocalDateTime likedAt) {
        LikedCourseView view = mock(LikedCourseView.class);
        lenient().when(view.getLikeId()).thenReturn(likeId);
        lenient().when(view.getCourseId()).thenReturn(courseId);
        lenient().when(view.getLikedAt()).thenReturn(likedAt);
        return view;
    }

    private MyCourseView myView(Long courseId, LocalDateTime createdAt) {
        MyCourseView view = mock(MyCourseView.class);
        lenient().when(view.getCourseId()).thenReturn(courseId);
        lenient().when(view.getCreatedAt()).thenReturn(createdAt);
        return view;
    }

    private MemberCourseCardView memberCourseCardView(Long courseId, LocalDateTime createdAt) {
        MemberCourseCardView view = mock(MemberCourseCardView.class);
        lenient().when(view.getCourseId()).thenReturn(courseId);
        lenient().when(view.getCreatedAt()).thenReturn(createdAt);
        return view;
    }

    private MemberCourseCardView memberCourseCardView(Long courseId, Long journalId, LocalDateTime createdAt) {
        MemberCourseCardView view = memberCourseCardView(courseId, createdAt);
        lenient().when(view.getJournalId()).thenReturn(journalId);
        return view;
    }

    @Test
    @DisplayName("좋아요 목록은 요청 크기보다 1개 더 조회해 다음 페이지 여부를 판단하고, 초과분은 잘라낸다")
    void getLikedCourses_hasNext() {
        // given: size 2를 요청했는데 3개가 조회되면 다음 페이지가 있다는 뜻이다
        LocalDateTime likedAt = LocalDateTime.of(2026, 7, 23, 12, 0);
        List<LikedCourseView> views = List.of(
                likedView(30L, 3L, likedAt), likedView(20L, 2L, likedAt), likedView(10L, 1L, likedAt));
        given(courseLikeRepository.findLikedCourses(eq(1L), any(Pageable.class))).willReturn(views);

        // when
        courseQueryService.getLikedCourses(1L, null, 2);

        // then: 조회는 3개(=2+1), 응답에 담기는 건 2개
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(courseLikeRepository).findLikedCourses(eq(1L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 3));

        ArgumentCaptor<List<LikedCourseView>> contentCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
        verify(courseConverter).toLikedListResponse(contentCaptor.capture(), any(), cursorCaptor.capture(), eq(true));
        assertThat(contentCaptor.getValue()).hasSize(2);

        // 다음 커서는 마지막 항목 기준(좋아요 시각 + course_like.id)으로 만들어진다
        CursorData nextCursor = CursorData.decode(cursorCaptor.getValue());
        assertThat(nextCursor.id()).isEqualTo(20L);
        assertThat(nextCursor.dateTimeValue()).isEqualTo(likedAt);
        assertThat(nextCursor.longValue()).isNull();
    }

    @Test
    @DisplayName("마지막 페이지면 다음 커서 없이 응답한다")
    void getLikedCourses_lastPage() {
        // given: likedView가 내부에서 스터빙하므로 given(...) 안에서 호출하면 중첩 스터빙이 된다. 미리 만들어 둔다.
        LikedCourseView view = likedView(10L, 1L, LocalDateTime.of(2026, 7, 23, 12, 0));
        given(courseLikeRepository.findLikedCourses(eq(1L), any(Pageable.class))).willReturn(List.of(view));

        // when
        courseQueryService.getLikedCourses(1L, null, 2);

        // then
        verify(courseConverter).toLikedListResponse(any(), any(), eq(null), eq(false));
    }

    @Test
    @DisplayName("커서를 주면 그 시각 이후 페이지를 조회한다")
    void getLikedCourses_withCursor() {
        // given
        LocalDateTime likedAt = LocalDateTime.of(2026, 7, 23, 12, 0);
        String cursor = new CursorData(20L, null, likedAt).encode();
        given(courseLikeRepository.findLikedCoursesAfterCursor(eq(1L), eq(likedAt), eq(20L), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getLikedCourses(1L, cursor, 2);

        // then: 첫 페이지 쿼리는 타지 않는다
        verify(courseLikeRepository, never()).findLikedCourses(any(), any());
        verify(courseLikeRepository).findLikedCoursesAfterCursor(eq(1L), eq(likedAt), eq(20L), any(Pageable.class));
    }

    @Test
    @DisplayName("정렬 기준과 맞지 않는 커서면 예외가 발생한다")
    void getLikedCourses_invalidCursor() {
        // given: 시간순 목록인데 숫자 커서(인기순용)가 들어온 경우
        String cursor = new CursorData(20L, 100L, null).encode();

        // when & then
        assertThatThrownBy(() -> courseQueryService.getLikedCourses(1L, cursor, 2))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(GlobalErrorCode.INVALID_CURSOR.getMessage());
    }

    @Test
    @DisplayName("size가 허용 범위를 벗어나면 예외가 발생한다")
    void getLikedCourses_invalidSize() {
        assertThatThrownBy(() -> courseQueryService.getLikedCourses(1L, null, 0))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(GlobalErrorCode.INVALID_PAGE_SIZE.getMessage());
        assertThatThrownBy(() -> courseQueryService.getLikedCourses(1L, null, 51))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(GlobalErrorCode.INVALID_PAGE_SIZE.getMessage());
    }

    @Test
    @DisplayName("size를 생략하면 기본 크기로 조회한다")
    void getLikedCourses_defaultSize() {
        // given
        given(courseLikeRepository.findLikedCourses(eq(1L), any(Pageable.class))).willReturn(List.of());

        // when
        courseQueryService.getLikedCourses(1L, null, null);

        // then: 기본 10 + hasNext 판단용 1
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(courseLikeRepository).findLikedCourses(eq(1L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 11));
    }

    // ---------- 저장 탭 - 내가 만든 코스 목록 ----------

    @Test
    @DisplayName("내 코스 목록은 호선/역 필터를 그대로 조회 조건으로 넘긴다")
    void getMyCourses_passesFilters() {
        // given
        given(courseRepository.findMyCourses(eq(1L), eq(6L), eq(9L), any(Pageable.class))).willReturn(List.of());
        given(courseRepository.findAvailableLines(1L)).willReturn(List.of());

        // when
        courseQueryService.getMyCourses(1L, 6L, 9L, null, 10);

        // then
        verify(courseRepository).findMyCourses(eq(1L), eq(6L), eq(9L), any(Pageable.class));
    }

    @Test
    @DisplayName("필터를 주지 않으면 null로 넘겨 조건을 걸지 않는다")
    void getMyCourses_withoutFilters() {
        // given
        given(courseRepository.findMyCourses(eq(1L), eq(null), eq(null), any(Pageable.class))).willReturn(List.of());
        given(courseRepository.findAvailableLines(1L)).willReturn(List.of());

        // when
        courseQueryService.getMyCourses(1L, null, null, null, 10);

        // then
        verify(courseRepository).findMyCourses(eq(1L), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("선택 가능한 호선은 최초 조회에서만 계산한다")
    void getMyCourses_availableLinesOnlyOnFirstPage() {
        // given: 필터 칩은 화면에 한 번만 그리므로 다음 페이지에서는 다시 조회하지 않는다
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 23, 12, 0);
        String cursor = new CursorData(5L, null, createdAt).encode();
        given(courseRepository.findMyCoursesAfterCursor(eq(1L), any(), any(), eq(createdAt), eq(5L), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getMyCourses(1L, null, null, cursor, 10);

        // then
        verify(courseRepository, never()).findAvailableLines(any());
        ArgumentCaptor<List<LineView>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(courseConverter).toMyListResponse(any(), any(), linesCaptor.capture(), any(), eq(false));
        assertThat(linesCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("커서가 빈 문자열이면 최초 조회로 보고 선택 가능한 호선도 함께 내려준다")
    void getMyCourses_blankCursorIsFirstPage() {
        // given: 빈 문자열은 커서 없음으로 취급된다. 조회는 첫 페이지인데 칩만 빠지는 일이 없어야 한다.
        given(courseRepository.findMyCourses(eq(1L), any(), any(), any(Pageable.class))).willReturn(List.of());
        given(courseRepository.findAvailableLines(1L)).willReturn(List.of());

        // when
        courseQueryService.getMyCourses(1L, null, null, "", 10);

        // then
        verify(courseRepository).findMyCourses(eq(1L), any(), any(), any(Pageable.class));
        verify(courseRepository).findAvailableLines(1L);
    }

    @Test
    @DisplayName("내 코스 목록의 다음 커서는 마지막 코스의 생성 시각과 id로 만든다")
    void getMyCourses_nextCursor() {
        // given: myView가 내부에서 스터빙하므로 given(...) 안에서 호출하면 중첩 스터빙이 된다. 미리 만들어 둔다.
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 23, 12, 0);
        List<MyCourseView> views = List.of(myView(3L, createdAt), myView(2L, createdAt));
        given(courseRepository.findMyCourses(eq(1L), any(), any(), any(Pageable.class))).willReturn(views);
        given(courseRepository.findAvailableLines(1L)).willReturn(List.of());

        // when: size 1 요청 → 2개 조회되어 다음 페이지 있음
        courseQueryService.getMyCourses(1L, null, null, null, 1);

        // then
        ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
        verify(courseConverter).toMyListResponse(any(), any(), any(), cursorCaptor.capture(), eq(true));
        CursorData nextCursor = CursorData.decode(cursorCaptor.getValue());
        assertThat(nextCursor.id()).isEqualTo(3L);
        assertThat(nextCursor.dateTimeValue()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("내 코스 목록은 완료한 코스 id 집합을 조회해 변환에 넘긴다")
    void getMyCourses_passesCompletedCourseIds() {
        // given
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 28, 12, 0);
        List<MyCourseView> views = List.of(myView(3L, createdAt), myView(7L, createdAt));
        given(courseRepository.findMyCourses(eq(1L), any(), any(), any(Pageable.class))).willReturn(views);
        given(courseRepository.findAvailableLines(1L)).willReturn(List.of());
        given(memberStampQueryService.getCompletedCourseIds(1L, List.of(3L, 7L))).willReturn(Set.of(7L));

        // when
        courseQueryService.getMyCourses(1L, null, null, null, 10);

        // then
        ArgumentCaptor<Set<Long>> completedCaptor = ArgumentCaptor.forClass(Set.class);
        verify(courseConverter).toMyListResponse(any(), completedCaptor.capture(), any(), any(), eq(false));
        assertThat(completedCaptor.getValue()).containsExactly(7L);
    }

    @Test
    @DisplayName("완료 여부는 페이지에 실린 코스만 한 번에 조회한다 (다음 페이지 확인용 초과분 제외)")
    void getMyCourses_completedLookupExcludesExtraRow() {
        // given: size 1 요청 → 2개 조회되지만 초과분은 응답에서 잘리므로 조회 대상도 아니다
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 28, 12, 0);
        List<MyCourseView> views = List.of(myView(3L, createdAt), myView(2L, createdAt));
        given(courseRepository.findMyCourses(eq(1L), any(), any(), any(Pageable.class))).willReturn(views);
        given(courseRepository.findAvailableLines(1L)).willReturn(List.of());

        // when
        courseQueryService.getMyCourses(1L, null, null, null, 1);

        // then
        verify(memberStampQueryService).getCompletedCourseIds(1L, List.of(3L));
    }

    // 프로젝션은 인터페이스라 mock으로 만든다. name은 코스 이름이 아니라 여행일지 제목(journal.title)이다.
    private PopularCourseView popularCourseView(Long courseId, String name, int viewCount, int likeCount) {
        PopularCourseView view = mock(PopularCourseView.class);
        lenient().when(view.getCourseId()).thenReturn(courseId);
        lenient().when(view.getName()).thenReturn(name);
        lenient().when(view.getViewCount()).thenReturn(viewCount);
        lenient().when(view.getLikeCount()).thenReturn(likeCount);
        return view;
    }

    @Test
    @DisplayName("역별 인기 코스를 limit 개수만큼 조회해 변환 결과를 반환한다")
    void getPopularCoursesByStation_success() {
        // 인기순 필터/정렬 자체는 리포지토리 쿼리 책임이고,
        // 여기서는 서비스가 limit을 Pageable로 넘기고 변환 결과를 반환하는지 확인한다.
        List<PopularCourseView> courses = List.of(
                popularCourseView(1L, "보문역에서 하루", 300, 128),
                popularCourseView(2L, "성수 코스", 200, 50));
        List<PopularCourseResponse> responses = List.of(
                new PopularCourseResponse(1L, "보문역에서 하루", 300, 128, false),
                new PopularCourseResponse(2L, "성수 코스", 200, 50, false));
        given(courseRepository.findPopularPublicCoursesByStationId(eq(6L), any(Pageable.class))).willReturn(courses);
        given(courseConverter.toPopularResponses(eq(courses), any())).willReturn(responses);

        // when
        List<PopularCourseResponse> result = courseQueryService.getPopularCoursesByStation(6L, 3);

        // then
        assertThat(result).isEqualTo(responses);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(courseRepository).findPopularPublicCoursesByStationId(eq(6L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 3));
    }

    // ---------- 둘러보기 코스 목록 ----------

    private ExploreCourseView exploreView(Long courseId, LocalDateTime createdAt, int viewCount, int likeCount) {
        ExploreCourseView view = mock(ExploreCourseView.class);
        lenient().when(view.getCourseId()).thenReturn(courseId);
        lenient().when(view.getCreatedAt()).thenReturn(createdAt);
        lenient().when(view.getViewCount()).thenReturn(viewCount);
        lenient().when(view.getLikeCount()).thenReturn(likeCount);
        return view;
    }

    private LineView lineView(Long id, String name, LineCode code) {
        LineView view = mock(LineView.class);
        lenient().when(view.getLineId()).thenReturn(id);
        lenient().when(view.getLineName()).thenReturn(name);
        lenient().when(view.getLineCode()).thenReturn(code);
        return view;
    }

    private StationView stationView(Long id, String name) {
        StationView view = mock(StationView.class);
        lenient().when(view.getStationId()).thenReturn(id);
        lenient().when(view.getStationName()).thenReturn(name);
        return view;
    }

    @Test
    @DisplayName("노선 칩은 코스가 없는 노선도 담고 hasCourses로 구분한다")
    void getExploreLines_marksLinesWithoutCourses() {
        // given: 코스 없는 노선을 빼면 데이터가 쌓일 때마다 칩이 늘어나 노선도가 흔들려 보인다
        // 프로젝션 mock에 스터빙이 들어 있어 given(...) 밖에서 미리 만든다
        LineView line1 = lineView(4L, "1호선", LineCode.LINE_1);
        LineView line2 = lineView(9L, "2호선", LineCode.LINE_2);
        given(courseRepository.findDrawableLines(any())).willReturn(List.of(line1, line2));
        given(courseRepository.findLinesWithPublicCourses()).willReturn(List.of(line2));

        // when
        List<ExploreLineResponse> result = courseQueryService.getExploreLines();

        // then
        assertThat(result).extracting(ExploreLineResponse::id, ExploreLineResponse::hasCourses)
                .containsExactly(tuple(4L, false), tuple(9L, true));
    }

    @Test
    @DisplayName("노선 칩은 화면에 칩이 있는 1~9호선만 조회한다")
    void getExploreLines_limitsToChipLines() {
        // given: 뽑기 역 중 환승역이 경의중앙선·우이신설선에도 속하지만 그 두 노선은 칩이 없다
        given(courseRepository.findDrawableLines(any())).willReturn(List.of());
        given(courseRepository.findLinesWithPublicCourses()).willReturn(List.of());

        // when
        courseQueryService.getExploreLines();

        // then: 경의중앙선·우이신설선은 조회 조건에서 빠진다
        verify(courseRepository).findDrawableLines(Set.of(LineCode.LINE_1, LineCode.LINE_2,
                LineCode.LINE_3, LineCode.LINE_4, LineCode.LINE_5, LineCode.LINE_6,
                LineCode.LINE_7, LineCode.LINE_8, LineCode.LINE_9));
    }

    @Test
    @DisplayName("역 필터 목록은 코스가 없는 역도 담고 hasCourses로 구분한다")
    void getExploreCourses_marksStationsWithoutCourses() {
        // given
        StationView bomun = stationView(123L, "보문역");
        StationView oksu = stationView(232L, "옥수역");
        given(courseRepository.findExploreCoursesByLatest(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());
        given(courseRepository.findDrawableStations(any())).willReturn(List.of(bomun, oksu));
        given(courseRepository.findStationsWithPublicCourses(any())).willReturn(List.of(oksu));

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, null, null), CourseSort.LATEST, null, null);

        // then: 후보 역은 둘 다 넘기고, 코스가 있는 역만 표시하도록 id 집합을 함께 넘긴다
        ArgumentCaptor<List<StationView>> stationsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Set<Long>> withCoursesCaptor = ArgumentCaptor.forClass(Set.class);
        verify(courseConverter).toExploreListResponse(
                any(), stationsCaptor.capture(), withCoursesCaptor.capture(), any(), eq(false));
        assertThat(stationsCaptor.getValue()).hasSize(2);
        assertThat(withCoursesCaptor.getValue()).containsExactly(232L);
    }

    @Test
    @DisplayName("역 필터 목록은 노선만 반영하고 역 필터는 반영하지 않는다")
    void getExploreCourses_availableStationsIgnoresStationFilter() {
        // given: 고른 역으로 좁히면 드롭다운에 그 역만 남아 다른 역으로 바꿀 수 없다
        given(courseRepository.findExploreCoursesByLatest(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());
        given(courseRepository.findDrawableStations(2L)).willReturn(List.of());
        given(courseRepository.findStationsWithPublicCourses(2L)).willReturn(List.of());

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(2L, 123L, null, null), CourseSort.LATEST, null, null);

        // then
        verify(courseRepository).findDrawableStations(2L);
        verify(courseRepository).findStationsWithPublicCourses(2L);
    }

    @Test
    @DisplayName("둘러보기 카드 배경은 여행일지 첫 사진을 쓴다")
    void getExploreCourses_cardImageFromJournal() {
        // given: 일지 11번에는 사진이 있고, 12번에는 없다
        LocalDateTime now = LocalDateTime.now();
        ExploreCourseView withImage = exploreView(1L, now, 0, 0);
        ExploreCourseView withoutImage = exploreView(2L, now, 0, 0);
        lenient().when(withImage.getJournalId()).thenReturn(11L);
        lenient().when(withoutImage.getJournalId()).thenReturn(12L);
        given(courseRepository.findExploreCoursesByLatest(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of(withImage, withoutImage));
        given(coursePlaceRepository.findByCourseIdInOrderByCourseIdAscOrderNumAsc(any())).willReturn(List.of());
        given(placeInfoQueryService.getTagNamesByPlace(any())).willReturn(Map.of());
        given(journalCardQueryService.getJournalCourseCardInfos(List.of(11L, 12L)))
                .willReturn(Map.of(
                        11L, new JournalCardInfoResponse(11L, "cover.jpg", null),
                        12L, new JournalCardInfoResponse(12L, null, null)));

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, null, null), CourseSort.LATEST, null, null);

        // then
        ArgumentCaptor<String> imageCaptor = ArgumentCaptor.forClass(String.class);
        verify(courseConverter, times(2))
                .toExploreCourseResponse(any(), any(), anyBoolean(), imageCaptor.capture());
        assertThat(imageCaptor.getAllValues()).containsExactly("cover.jpg", null);
    }

    @Test
    @DisplayName("검색 요청에는 역 필터 목록을 계산하지 않는다")
    void getExploreCourses_noStationsWhenSearching() {
        // given: 검색 결과 화면에는 "역 선택"이 없어 후보 역 50개를 실어 보낼 이유가 없다
        given(courseRepository.findExploreCoursesByLatest(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, "신림", null), CourseSort.LATEST, null, null);

        // then
        verify(courseRepository, never()).findDrawableStations(any());
        verify(courseRepository, never()).findStationsWithPublicCourses(any());
    }

    @Test
    @DisplayName("역 필터 목록은 최초 조회에서만 계산한다")
    void getExploreCourses_availableStationsOnlyOnFirstPage() {
        // given: 드롭다운은 화면에 한 번만 그리므로 다음 페이지에서는 조회하지 않는다
        String cursor = new CursorData(1L, null, LocalDateTime.now()).encode();
        given(courseRepository.findExploreCoursesByLatest(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(2L, null, null, null), CourseSort.LATEST, cursor, null);

        // then
        verify(courseRepository, never()).findDrawableStations(any());
    }

    @Test
    @DisplayName("다른 목록의 커서를 많이 찾는 코스에 넣으면 400이다")
    void getMostLikedCourses_rejectsForeignCursor() {
        // given: 인기순 커서의 점수를 시작 위치로 읽으면 조용히 빈 목록이 된다
        String popularCursor = new CursorData(1L, 322L, LocalDateTime.now()).encode();

        // when & then
        assertThatThrownBy(() -> courseQueryService.getMostLikedCourses(null, popularCursor, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.INVALID_CURSOR);
    }

    @Test
    @DisplayName("둘러보기 메인의 노선 코스는 역 목록을 조회하지 않는다")
    void getLineCourses_noAvailableStations() {
        // given: 메인 화면에는 역 선택이 없어 계산해도 응답에 담기지 않는다
        given(courseRepository.findExploreCoursesByPopular(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());
        given(courseConverter.toExploreListResponse(any(), any(), any(), any(), anyBoolean()))
                .willReturn(new ExploreCourseListResponse(List.of(), List.of(), null, false));

        // when
        courseQueryService.getLineCourses(null, 6L, 3);

        // then
        verify(courseRepository, never()).findDrawableStations(any());
        verify(courseRepository, never()).findStationsWithPublicCourses(any());
    }

    @Test
    @DisplayName("둘러보기 메인의 노선 코스는 목록 API와 같은 기본 정렬(인기순)로 조회한다")
    void getLineCourses_usesDefaultSort() {
        // given: 여기만 정렬이 다르면 더보기로 넘어갔을 때 미리보기의 코스가 사라진 것처럼 보인다
        given(courseRepository.findExploreCoursesByPopular(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());
        given(courseConverter.toExploreListResponse(any(), any(), any(), any(), anyBoolean()))
                .willReturn(new ExploreCourseListResponse(List.of(), List.of(), null, false));

        // when
        courseQueryService.getLineCourses(null, 6L, 3);

        // then
        verify(courseRepository)
                .findExploreCoursesByPopular(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class));
        verify(courseRepository, never())
                .findExploreCoursesByLatest(any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("컨셉별 코스 목록은 역 선택 드롭다운이 없어 역 목록을 조회하지 않는다")
    void getConceptTourCourses_noAvailableStations() {
        // given: 컨셉 상세에는 정렬 토글만 있어 후보 역 50개를 실어 보낼 이유가 없다
        given(courseRepository.findExploreCoursesByLatest(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getConceptTourCourses(null, 1L, CourseSort.LATEST, null, null);

        // then
        verify(courseRepository, never()).findDrawableStations(any());
        verify(courseRepository, never()).findStationsWithPublicCourses(any());
    }

    @Test
    @DisplayName("컨셉별 코스 목록은 컨셉 id를 조회 조건으로 넘긴다")
    void getConceptTourCourses_passesConceptTourId() {
        // given
        given(courseRepository.findExploreCoursesByLatest(any(), any(), any(), eq(7L), any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getConceptTourCourses(null, 7L, CourseSort.LATEST, null, null);

        // then
        verify(courseRepository)
                .findExploreCoursesByLatest(isNull(), isNull(), isNull(), eq(7L), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("많이 찾는 코스는 역 선택 드롭다운이 없어 역 목록을 조회하지 않는다")
    void getMostLikedCourses_noAvailableStations() {
        // given
        given(courseRepository.findMostLikedCourses(any(Pageable.class))).willReturn(List.of());

        // when
        courseQueryService.getMostLikedCourses(null, null, null);

        // then
        verify(courseRepository, never()).findDrawableStations(any());
    }

    @Test
    @DisplayName("정렬을 생략하면 인기순으로 조회한다")
    void getExploreCourses_defaultsToPopular() {
        // given
        given(courseRepository.findExploreCoursesByPopular(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, null, null), null, null, null);

        // then: 정렬이 없으면 페이지마다 순서가 흔들려 커서 페이징이 성립하지 않는다
        verify(courseRepository)
                .findExploreCoursesByPopular(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class));
        verify(courseRepository, never())
                .findExploreCoursesByLatest(any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("검색어의 꼬리 \"역\"은 떼고 조회한다")
    void getExploreCourses_normalizesKeyword() {
        // given: 역명은 쿼리에서 꼬리를 떼고 비교하므로 검색어도 같은 규칙으로 맞춰야 걸린다
        given(courseRepository.findExploreCoursesByLatest(any(), any(), eq("왕십리"), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, "왕십리역", null), CourseSort.LATEST, null, null);

        // then
        verify(courseRepository)
                .findExploreCoursesByLatest(any(), any(), eq("왕십리"), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("검색어의 LIKE 와일드카드는 이스케이프해서 문자 그대로 찾는다")
    void getExploreCourses_escapesLikeWildcards() {
        // given: 이스케이프하지 않으면 "%" 한 글자로 공개 코스 전체가 조회된다
        given(courseRepository.findExploreCoursesByLatest(any(), any(), eq("!%50!_"), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, "%50_", null), CourseSort.LATEST, null, null);

        // then
        verify(courseRepository)
                .findExploreCoursesByLatest(any(), any(), eq("!%50!_"), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("이스케이프 문자 자체가 검색어에 있으면 두 번 겹쳐 문자로 취급한다")
    void getExploreCourses_escapesEscapeCharacter() {
        // given
        given(courseRepository.findExploreCoursesByLatest(any(), any(), eq("!!느낌표"), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, "!느낌표", null), CourseSort.LATEST, null, null);

        // then
        verify(courseRepository)
                .findExploreCoursesByLatest(any(), any(), eq("!!느낌표"), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("빈 검색어는 조건에서 빼고 전체를 조회한다")
    void getExploreCourses_blankKeyword() {
        // given
        given(courseRepository.findExploreCoursesByLatest(any(), any(), isNull(), any(), any(), any(), any(Pageable.class)))
                .willReturn(List.of());

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, "  ", null), CourseSort.LATEST, null, null);

        // then
        verify(courseRepository)
                .findExploreCoursesByLatest(any(), any(), isNull(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("인기순 커서의 점수는 int 범위를 넘어도 그대로 담긴다")
    void getExploreCourses_scoreDoesNotOverflow() {
        // given: 조회수·좋아요는 int라 int로 더하면 넘친다. 쿼리 쪽은 DB가 BIGINT로 계산하므로
        // 커서 값도 long이어야 비교값과 어긋나지 않는다.
        LocalDateTime now = LocalDateTime.now();
        List<ExploreCourseView> found = List.of(
                exploreView(1L, now, 2_000_000_000, 1_000_000_000),
                exploreView(2L, now, 0, 0));
        given(courseRepository.findExploreCoursesByPopular(
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(found);
        given(coursePlaceRepository.findByCourseIdInOrderByCourseIdAscOrderNumAsc(any())).willReturn(List.of());
        given(placeInfoQueryService.getTagNamesByPlace(any())).willReturn(Map.of());

        // when: size 1이라 첫 코스로 커서를 만든다
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, null, null), CourseSort.POPULAR, null, 1);

        // then: 2,000,000,000 + 1,000,000,000 × 2 = 4,000,000,000
        ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
        verify(courseConverter).toExploreListResponse(any(), any(), any(), cursorCaptor.capture(), eq(true));
        assertThat(CursorData.decode(cursorCaptor.getValue()).longValue()).isEqualTo(4_000_000_000L);
    }

    @Test
    @DisplayName("다음 페이지가 있으면 마지막 코스로 커서를 만들고 초과분은 잘라낸다")
    void getExploreCourses_paging() {
        // given: hasNext 판단용으로 size + 1개를 조회한다.
        // 목 생성은 스터빙 밖에서 끝내야 Mockito가 중첩 스터빙으로 보지 않는다.
        LocalDateTime now = LocalDateTime.now();
        List<ExploreCourseView> found =
                List.of(exploreView(1L, now, 0, 0), exploreView(2L, now, 0, 0), exploreView(3L, now, 0, 0));
        given(courseRepository.findExploreCoursesByLatest(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(found);
        given(coursePlaceRepository.findByCourseIdInOrderByCourseIdAscOrderNumAsc(any())).willReturn(List.of());
        given(placeInfoQueryService.getTagNamesByPlace(any())).willReturn(Map.of());

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, null, null), CourseSort.LATEST, null, 2);

        // then: 초과분 1개는 빼고 커서를 만든다
        ArgumentCaptor<List<ExploreCourseResponse>> coursesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
        verify(courseConverter).toExploreListResponse(
                coursesCaptor.capture(), any(), any(), cursorCaptor.capture(), eq(true));
        assertThat(coursesCaptor.getValue()).hasSize(2);
        assertThat(cursorCaptor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("인기순 커서를 최신순 조회에 쓰면 400이다")
    void getExploreCourses_cursorSortMismatch() {
        // given: 인기순 커서에는 점수가 들어 있어 최신순 조회에 그대로 쓰면 순서가 뒤엉킨다
        String popularCursor = new CursorData(1L, 50L, LocalDateTime.now()).encode();

        // when & then
        assertThatThrownBy(() -> courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, null, null), CourseSort.LATEST, popularCursor, null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(GlobalErrorCode.INVALID_CURSOR.getMessage());
    }

    @Test
    @DisplayName("비로그인이면 좋아요 여부를 조회하지 않고 전부 false로 내린다")
    void getExploreCourses_anonymousHasNoLikes() {
        // given
        List<ExploreCourseView> found = List.of(exploreView(1L, LocalDateTime.now(), 0, 0));
        given(courseRepository.findExploreCoursesByLatest(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .willReturn(found);
        given(coursePlaceRepository.findByCourseIdInOrderByCourseIdAscOrderNumAsc(any())).willReturn(List.of());
        given(placeInfoQueryService.getTagNamesByPlace(any())).willReturn(Map.of());

        // when
        courseQueryService.getExploreCourses(
                null, new ExploreCourseCondition(null, null, null, null), CourseSort.LATEST, null, null);

        // then: 누를 사람이 없으므로 조회 자체를 하지 않는다
        verify(courseLikeRepository, never()).findLikedCourseIds(any(), any());
    }

    // ---------- 사람들이 많이 찾는 코스 ----------

    @Test
    @DisplayName("많이 찾는 코스는 상위 30개까지만 조회한다")
    void getMostLikedCourses_limitedTo30() {
        // given
        given(courseRepository.findMostLikedCourses(any(Pageable.class))).willReturn(List.of());

        // when
        courseQueryService.getMostLikedCourses(null, null, null);

        // then: 무한스크롤도 30번째에서 끝난다
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(courseRepository).findMostLikedCourses(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(30);
    }

    @Test
    @DisplayName("많이 찾는 코스의 커서는 다음 시작 위치를 담는다")
    void getMostLikedCourses_offsetCursor() {
        // given: 좋아요 수는 수시로 바뀌어 값 기준 커서를 쓰면 순위가 흔들릴 때 결과가 어긋난다
        LocalDateTime now = LocalDateTime.now();
        List<ExploreCourseView> found =
                List.of(exploreView(1L, now, 0, 9), exploreView(2L, now, 0, 6), exploreView(3L, now, 0, 3));
        given(courseRepository.findMostLikedCourses(any(Pageable.class))).willReturn(found);
        given(coursePlaceRepository.findByCourseIdInOrderByCourseIdAscOrderNumAsc(any())).willReturn(List.of());
        given(placeInfoQueryService.getTagNamesByPlace(any())).willReturn(Map.of());

        // when
        courseQueryService.getMostLikedCourses(null, null, 2);

        // then
        ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
        verify(courseConverter).toExploreListResponse(any(), any(), any(), cursorCaptor.capture(), eq(true));
        assertThat(CursorData.decode(cursorCaptor.getValue()).longValue()).isEqualTo(2L);
    }

    @Test
    @DisplayName("커서 위치가 목록 끝을 넘으면 빈 목록을 반환한다")
    void getMostLikedCourses_cursorBeyondEnd() {
        // given: 사이에 코스가 줄어 위치가 범위를 벗어나도 오류 없이 끝으로 처리한다
        List<ExploreCourseView> found = List.of(exploreView(1L, LocalDateTime.now(), 0, 9));
        given(courseRepository.findMostLikedCourses(any(Pageable.class))).willReturn(found);
        String cursor = new CursorData(null, 10L, null).encode();

        // when
        courseQueryService.getMostLikedCourses(null, cursor, null);

        // then
        verify(courseConverter).toExploreListResponse(List.of(), List.of(), Set.of(), null, false);
    }

    @Test
    @DisplayName("위치가 없는 커서는 400이다")
    void getMostLikedCourses_invalidCursor() {
        // given: 이 목록의 커서에는 위치(longValue)가 반드시 들어 있어야 한다
        String timeCursor = new CursorData(1L, null, LocalDateTime.now()).encode();

        // when & then
        assertThatThrownBy(() -> courseQueryService.getMostLikedCourses(null, timeCursor, null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(GlobalErrorCode.INVALID_CURSOR.getMessage());
    }

    // ---------- 좋아요 여부 창구 ----------

    @Test
    @DisplayName("좋아요한 코스면 true를 반환한다")
    void isLikedByMember_liked() {
        // given
        given(courseLikeRepository.existsByMemberIdAndCourseId(1L, 10L)).willReturn(true);

        // when & then
        assertThat(courseQueryService.isLikedByMember(10L, 1L)).isTrue();
    }

    @Test
    @DisplayName("비로그인이면 조회하지 않고 false를 반환한다")
    void isLikedByMember_anonymous() {
        // when: 누를 사람이 없으므로 하트는 항상 비어 있다
        boolean result = courseQueryService.isLikedByMember(10L, null);

        // then
        assertThat(result).isFalse();
        verify(courseLikeRepository, never()).existsByMemberIdAndCourseId(any(), any());
    }

    // ---------- 코스 확인 (내가 만든 코스 단건) ----------

    private PlaceInfoResponse placeInfo(Long placeId, Double x, Double y) {
        return new PlaceInfoResponse(placeId, "장소" + placeId, "설명", "FOOD", "식당", "img" + placeId, x, y);
    }

    private HistoricalPlaceInfoResponse historicalPlaceInfo(Long placeId, Double x, Double y) {
        return new HistoricalPlaceInfoResponse(
                placeId, "장소" + placeId, "설명", "FOOD", "식당", "img" + placeId, x, y, PlaceStatus.APPROVED);
    }

    @Test
    @DisplayName("코스 확인은 역의 대표 호선을 함께 내려준다")
    void getMyCourseDetail_includesLine() {
        // given: 화면 상단 배지가 호선에 따라 달라져서 대표 호선이 필요하다
        CourseDetailView view = mock(CourseDetailView.class);
        given(courseRepository.findMyCourseDetail(1L, 1L)).willReturn(Optional.of(view));
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(1L)).willReturn(List.of());

        // when
        courseQueryService.getMyCourseDetail(1L, 1L);

        // then: 조회 결과를 그대로 컨버터에 넘긴다
        verify(courseConverter).toMyCourseDetailResponse(view, List.of());
    }

    @Test
    @DisplayName("내 코스로 만들기 화면은 공개 코스만 조회한다")
    void getCourseCopyPreview_success() {
        // given
        CourseDetailView view = mock(CourseDetailView.class);
        given(courseRepository.findPublicCourseDetail(7L)).willReturn(Optional.of(view));
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(7L)).willReturn(List.of());

        // when
        courseQueryService.getCourseCopyPreview(7L);

        // then: 소유자 조건 없이 공개 조건만 걸린 조회를 쓴다
        verify(courseRepository).findPublicCourseDetail(7L);
        verify(courseConverter).toCourseCopyPreviewResponse(view, List.of());
    }

    @Test
    @DisplayName("공개되지 않은 코스의 내 코스로 만들기 화면을 조회하면 예외가 발생한다")
    void getCourseCopyPreview_notPublic() {
        // given: 비공개·일지 없음·삭제는 조회 단계에서 함께 걸러진다
        given(courseRepository.findPublicCourseDetail(7L)).willReturn(Optional.empty());

        // when & then: 남의 비공개 코스가 존재한다는 사실을 드러내지 않도록 404다
        assertThatThrownBy(() -> courseQueryService.getCourseCopyPreview(7L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("공유 링크로 코스 확인은 소유자·공개 여부를 따지지 않고 shareToken으로만 조회한다")
    void getCourseShareDetail_success() {
        // given
        CourseDetailView view = mock(CourseDetailView.class);
        given(view.getCourseId()).willReturn(7L);
        given(courseRepository.findShareCourseDetail("share-token-7")).willReturn(Optional.of(view));
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(7L)).willReturn(List.of());

        // when
        courseQueryService.getCourseShareDetail("share-token-7");

        // then: 소유자 조건도, 공개(journal) 조건도 없이 shareToken만으로 조회한다 (courseId는 노출되지 않는다)
        verify(courseRepository).findShareCourseDetail("share-token-7");
        verify(courseConverter).toCourseShareResponse(view, List.of());
    }

    @Test
    @DisplayName("존재하지 않는 토큰으로 공유 링크를 조회하면 예외가 발생한다")
    void getCourseShareDetail_notFound() {
        // given
        given(courseRepository.findShareCourseDetail("invalid-token")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> courseQueryService.getCourseShareDetail("invalid-token"))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("코스 확인은 장소 조회 결과 순서와 무관하게 order_num 순으로 채운다")
    void getMyCourseDetail_ordersPlacesByOrderNum() {
        // given: 코스 순서는 20 -> 10 인데 장소 조회는 10 -> 20 으로 돌려준다
        CourseDetailView view = mock(CourseDetailView.class);
        given(courseRepository.findMyCourseDetail(1L, 1L)).willReturn(Optional.of(view));
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(1L))
                .willReturn(List.of(coursePlace(1L, 20L, 1), coursePlace(1L, 10L, 2)));
        given(placeInfoQueryService.getHistoricalPlaceInfos(List.of(20L, 10L)))
                .willReturn(List.of(historicalPlaceInfo(10L, 127.1, 37.1), historicalPlaceInfo(20L, 127.2, 37.2)));
        given(courseConverter.toCoursePlaceDetailResponse(any(), anyInt())).willAnswer(invocation -> {
            HistoricalPlaceInfoResponse place = invocation.getArgument(0);
            return new CoursePlaceDetailResponse(place.placeId(), place.placeName(), place.description(),
                    place.categoryCode(), place.categoryName(), place.imageUrl(),
                    place.xCoordinate(), place.yCoordinate(), place.placeStatus(), invocation.getArgument(1));
        });

        // when
        courseQueryService.getMyCourseDetail(1L, 1L);

        // then: 지도 핀 번호와 목록 순서가 어긋나지 않도록 코스 순서를 따른다
        ArgumentCaptor<List<CoursePlaceDetailResponse>> placesCaptor = ArgumentCaptor.forClass(List.class);
        verify(courseConverter).toMyCourseDetailResponse(eq(view), placesCaptor.capture());
        assertThat(placesCaptor.getValue())
                .extracting(CoursePlaceDetailResponse::placeId, CoursePlaceDetailResponse::orderNum)
                .containsExactly(tuple(20L, 1), tuple(10L, 2));
    }

    @Test
    @DisplayName("코스 확인에서 조회되지 않는 장소는 응답에서 빠진다")
    void getMyCourseDetail_skipsMissingPlaces() {
        // given: 코스에 담긴 장소 중 10번만 조회된다
        CourseDetailView view = mock(CourseDetailView.class);
        given(courseRepository.findMyCourseDetail(1L, 1L)).willReturn(Optional.of(view));
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(1L))
                .willReturn(List.of(coursePlace(1L, 10L, 1), coursePlace(1L, 99L, 2)));
        given(placeInfoQueryService.getHistoricalPlaceInfos(List.of(10L, 99L)))
                .willReturn(List.of(historicalPlaceInfo(10L, 127.1, 37.1)));
        given(courseConverter.toCoursePlaceDetailResponse(any(), anyInt())).willAnswer(invocation -> {
            HistoricalPlaceInfoResponse place = invocation.getArgument(0);
            return new CoursePlaceDetailResponse(place.placeId(), place.placeName(), place.description(),
                    place.categoryCode(), place.categoryName(), place.imageUrl(),
                    place.xCoordinate(), place.yCoordinate(), place.placeStatus(), invocation.getArgument(1));
        });

        // when
        courseQueryService.getMyCourseDetail(1L, 1L);

        // then: 좌표가 없으면 지도 핀도 못 찍고 목록에 채울 내용도 없다
        ArgumentCaptor<List<CoursePlaceDetailResponse>> placesCaptor = ArgumentCaptor.forClass(List.class);
        verify(courseConverter).toMyCourseDetailResponse(eq(view), placesCaptor.capture());
        assertThat(placesCaptor.getValue()).extracting(CoursePlaceDetailResponse::placeId).containsExactly(10L);
    }

    @Test
    @DisplayName("본인 코스가 아니면 코스 확인은 404다")
    void getMyCourseDetail_notMine() {
        // given: 조회 조건에 memberId가 들어가 남의 코스는 애초에 나오지 않는다
        given(courseRepository.findMyCourseDetail(1L, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> courseQueryService.getMyCourseDetail(1L, 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_NOT_FOUND.getMessage());
        verify(placeInfoQueryService, never()).getHistoricalPlaceInfos(any());
    }

    @Test
    @DisplayName("장소가 없는 코스도 코스 확인은 빈 목록으로 응답한다")
    void getMyCourseDetail_noPlaces() {
        // given
        CourseDetailView view = mock(CourseDetailView.class);
        given(courseRepository.findMyCourseDetail(1L, 1L)).willReturn(Optional.of(view));
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(1L)).willReturn(List.of());

        // when
        courseQueryService.getMyCourseDetail(1L, 1L);

        // then: 빈 id 목록으로 장소를 조회하지 않는다
        verify(placeInfoQueryService, never()).getHistoricalPlaceInfos(any());
        verify(courseConverter).toMyCourseDetailResponse(view, List.of());
    }

    // ---------- 다른 회원 프로필의 공개 코스 개수 ----------

    @Test
    @DisplayName("공개 코스 개수를 그대로 반환한다")
    void countPublicCourses_returnsCount() {
        // given
        given(courseRepository.countPublicCoursesByMemberId(1L)).willReturn(5L);

        // when
        long publicCourseCount = courseQueryService.countPublicCourses(1L);

        // then
        assertThat(publicCourseCount).isEqualTo(5L);
    }

    // ---------- 다른 회원의 공개코스 탭 ----------

    @Test
    @DisplayName("존재하지 않는 회원의 공개 코스를 조회하면 예외가 발생하고 조회하지 않는다")
    void getMemberPublicCourses_memberNotFound() {
        // given
        given(memberExistenceQueryService.existsMember(2L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> courseQueryService.getMemberPublicCourses(2L, null, null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
        verify(courseRepository, never()).findPublicCoursesByMemberId(any(), any());
    }

    @Test
    @DisplayName("첫 페이지는 memberId로 공개 코스를 조회해 카드로 변환한다")
    void getMemberPublicCourses_firstPage() {
        // given
        given(memberExistenceQueryService.existsMember(2L)).willReturn(true);
        given(courseRepository.findPublicCoursesByMemberId(eq(2L), any(Pageable.class))).willReturn(List.of());
        MemberCourseListResponse expected = new MemberCourseListResponse(List.of(), null, false);
        given(courseConverter.toMemberCourseListResponse(List.of(), null, false)).willReturn(expected);

        // when
        MemberCourseListResponse response = courseQueryService.getMemberPublicCourses(2L, null, 10);

        // then
        assertThat(response).isEqualTo(expected);
        verify(courseRepository).findPublicCoursesByMemberId(eq(2L), any(Pageable.class));
    }

    @Test
    @DisplayName("다음 페이지 커서는 마지막 코스의 생성 시각과 id로 만든다")
    void getMemberPublicCourses_nextCursor() {
        // given: size 1 요청 → 2개 조회되어 다음 페이지 있음
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 23, 12, 0);
        List<MemberCourseCardView> views = List.of(memberCourseCardView(3L, createdAt), memberCourseCardView(2L, createdAt));
        given(memberExistenceQueryService.existsMember(2L)).willReturn(true);
        given(courseRepository.findPublicCoursesByMemberId(eq(2L), any(Pageable.class))).willReturn(views);

        // when
        courseQueryService.getMemberPublicCourses(2L, null, 1);

        // then
        ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
        verify(courseConverter).toMemberCourseListResponse(any(), cursorCaptor.capture(), eq(true));
        CursorData nextCursor = CursorData.decode(cursorCaptor.getValue());
        assertThat(nextCursor.id()).isEqualTo(3L);
        assertThat(nextCursor.dateTimeValue()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("공개 코스 카드는 journalId를 모아 한 번에 조회해 이미지를 붙인다")
    void getMemberPublicCourses_batchesJournalImages() {
        // given: 21번 일지는 사진이 있고, 22번은 없다
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 23, 12, 0);
        List<MemberCourseCardView> views = List.of(
                memberCourseCardView(3L, 21L, createdAt),
                memberCourseCardView(2L, 22L, createdAt));
        given(memberExistenceQueryService.existsMember(2L)).willReturn(true);
        given(courseRepository.findPublicCoursesByMemberId(eq(2L), any(Pageable.class))).willReturn(views);
        given(journalCardQueryService.getJournalCourseCardInfos(List.of(21L, 22L)))
                .willReturn(Map.of(
                        21L, new JournalCardInfoResponse(21L, "cover.jpg", null),
                        22L, new JournalCardInfoResponse(22L, null, null)));

        // when
        courseQueryService.getMemberPublicCourses(2L, null, 10);

        // then: 카드마다 조회했다면 journalId별로 여러 번 불렸겠지만, 모아서 한 번만 부른다
        verify(journalCardQueryService).getJournalCourseCardInfos(List.of(21L, 22L));
        ArgumentCaptor<String> imageCaptor = ArgumentCaptor.forClass(String.class);
        verify(courseConverter, times(2)).toMemberCourseCardResponse(any(), imageCaptor.capture());
        assertThat(imageCaptor.getAllValues()).containsExactly("cover.jpg", null);
    }

    @Test
    @DisplayName("커서가 있으면 다음 페이지 조회 쿼리로 넘어간다")
    void getMemberPublicCourses_afterCursor() {
        // given
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 23, 12, 0);
        String cursor = new CursorData(5L, null, createdAt).encode();
        given(memberExistenceQueryService.existsMember(2L)).willReturn(true);
        given(courseRepository.findPublicCoursesByMemberIdAfterCursor(
                eq(2L), eq(createdAt), eq(5L), any(Pageable.class))).willReturn(List.of());

        // when
        courseQueryService.getMemberPublicCourses(2L, cursor, 10);

        // then
        verify(courseRepository).findPublicCoursesByMemberIdAfterCursor(
                eq(2L), eq(createdAt), eq(5L), any(Pageable.class));
        verify(courseRepository, never()).findPublicCoursesByMemberId(any(), any());
    }
}
