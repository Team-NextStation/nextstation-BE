package com.cotato.nextstation.domain.course.service.query;

import com.cotato.nextstation.domain.course.converter.CourseConverter;
import com.cotato.nextstation.domain.course.dto.request.ExploreCourseCondition;
import com.cotato.nextstation.domain.course.dto.response.CourseInfoResponse;
import com.cotato.nextstation.domain.course.dto.response.CoursePlaceInfoResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreCourseListResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreCourseResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreLineResponse;
import com.cotato.nextstation.domain.course.dto.response.MyCourseDetailResponse;
import com.cotato.nextstation.domain.course.dto.response.MyCourseListResponse;
import com.cotato.nextstation.domain.course.dto.response.CourseCopyPreviewResponse;
import com.cotato.nextstation.domain.course.dto.response.CoursePlaceDetailResponse;
import com.cotato.nextstation.domain.course.dto.response.CourseShareResponse;
import com.cotato.nextstation.domain.course.dto.response.PlaceCourseResponse;
import com.cotato.nextstation.domain.course.dto.response.PopularCourseResponse;
import com.cotato.nextstation.domain.course.dto.response.LikedCourseListResponse;
import com.cotato.nextstation.domain.course.dto.response.MemberCourseCardResponse;
import com.cotato.nextstation.domain.course.dto.response.MemberCourseListResponse;
import com.cotato.nextstation.domain.course.entity.Course;
import com.cotato.nextstation.domain.course.entity.CoursePlace;
import com.cotato.nextstation.domain.course.entity.CourseSort;
import com.cotato.nextstation.domain.course.exception.CourseErrorCode;
import com.cotato.nextstation.domain.course.repository.CoursePlaceRepository;
import com.cotato.nextstation.domain.course.repository.CourseRepository;
import com.cotato.nextstation.domain.course.repository.CourseRepository.LineView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.ExploreCourseView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.CourseDetailView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.MyCourseView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.MemberCourseCardView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.StationView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.PlaceCourseView;
import com.cotato.nextstation.domain.course.repository.CourseRepository.PopularCourseView;
import com.cotato.nextstation.domain.course.repository.CourseLikeRepository;
import com.cotato.nextstation.domain.course.repository.CourseLikeRepository.LikedCourseView;
import com.cotato.nextstation.domain.journal.dto.response.JournalCardInfoResponse;
import com.cotato.nextstation.domain.journal.enums.TravelDuration;
import com.cotato.nextstation.domain.journal.service.query.JournalCardQueryService;
import com.cotato.nextstation.domain.member.exception.MemberErrorCode;
import com.cotato.nextstation.domain.member.service.query.MemberExistenceQueryService;
import com.cotato.nextstation.domain.place.dto.response.HistoricalPlaceInfoResponse;
import com.cotato.nextstation.domain.place.dto.response.PlaceInfoResponse;
import com.cotato.nextstation.domain.place.service.query.PlaceInfoQueryService;
import com.cotato.nextstation.domain.stamp.service.query.MemberStampQueryService;
import com.cotato.nextstation.domain.station.entity.LineCode;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.util.CursorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 코스 조회 전용 서비스.
// 저장 탭 목록 같은 화면 조회와, 다른 도메인이 코스를 참조할 때 쓰는 포트를 함께 제공한다.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseQueryService {

    private static final int DEFAULT_SIZE = 10;

    // TODO: 방어적으로 잡은 값. 추후 프론트 실제 요청 사이즈 확인 후 조정 필요.
    private static final int MAX_SIZE = 50;

    // 장소 상세 화면의 "이 장소를 포함한 코스"는 가로 스크롤 6개 고정이라 더보기가 없다.
    private static final int PLACE_COURSE_LIMIT = 6;

    // 카드에 노출할 태그 수 (디자인상 2개)
    private static final int TAGS_PER_CARD = 2;

    // 모든 역명이 이 접미사로 끝나 검색어로서 변별력이 없다. 역 검색과 같은 규칙으로 뗀다.
    private static final String STATION_NAME_SUFFIX = "역";

    // LIKE 이스케이프 문자. 코스 이름·역명에 쓰이지 않고 Java·JPQL·MySQL에서 중복 이스케이프될 일도 없다.
    private static final String LIKE_ESCAPE = "!";

    // "사람들이 많이 찾는 코스"는 상위 30개까지만 보여준다(무한스크롤도 여기서 끝난다).
    private static final int MOST_LIKED_LIMIT = 30;

    // 정렬 토글의 첫 항목이자 화면 진입 시 선택돼 있는 값. 토글이 없는 검색 결과도 이 정렬을 따른다.
    private static final CourseSort DEFAULT_SORT = CourseSort.POPULAR;

    // 둘러보기 노선 칩으로 그리는 노선. 화면에 1~9호선 칩만 있어서 그 밖의 노선은 응답에서 뺀다.
    // 뽑기 역 중 환승역이 경의중앙선·우이신설선에도 속해 있어, 소속 호선을 그대로 내리면
    // 칩이 없는 노선까지 목록에 섞인다. 그 역들의 코스는 함께 속한 1~9호선 칩에서 그대로 조회된다.
    // 뽑기 역이 다른 노선으로 늘어나면 프론트 칩과 함께 이 목록도 넓혀야 한다.
    private static final Set<LineCode> EXPLORE_CHIP_LINE_CODES = EnumSet.of(
            LineCode.LINE_1, LineCode.LINE_2, LineCode.LINE_3, LineCode.LINE_4, LineCode.LINE_5,
            LineCode.LINE_6, LineCode.LINE_7, LineCode.LINE_8, LineCode.LINE_9);

    private final CourseRepository courseRepository;
    private final CoursePlaceRepository coursePlaceRepository;
    private final CourseLikeRepository courseLikeRepository;
    private final PlaceInfoQueryService placeInfoQueryService;
    private final MemberStampQueryService memberStampQueryService;
    private final JournalCardQueryService journalCardQueryService;
    private final MemberExistenceQueryService memberExistenceQueryService;
    private final CourseConverter courseConverter;

    /**
     * 좋아요(하트)한 코스 목록 (최근 좋아요순).
     * 원본이 삭제되거나 비공개로 바뀐 코스는 조회 단계에서 빠진다.
     * 화면에 필터 칩이 없어 필터 파라미터를 받지 않는다.
     */
    public LikedCourseListResponse getLikedCourses(Long memberId, String cursor, Integer size) {
        int pageSize = resolvePageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1); // hasNext 판단용 1개 더 조회

        // 커서 해석은 한 번만 한다. 빈 문자열도 "커서 없음"으로 취급된다.
        CursorData cursorData = CursorData.decode(cursor);
        List<LikedCourseView> likedCourses = fetchLikedCourses(memberId, cursorData, pageable);

        boolean hasNext = likedCourses.size() > pageSize;
        List<LikedCourseView> pageContent = hasNext ? likedCourses.subList(0, pageSize) : likedCourses;

        String nextCursor = null;
        if (hasNext) {
            LikedCourseView last = pageContent.get(pageContent.size() - 1);
            nextCursor = new CursorData(last.getLikeId(), null, last.getLikedAt()).encode();
        }

        // 카드 배경은 작성자가 여행일지에 올린 첫 사진이다(둘러보기 카드와 같은 규칙).
        Map<Long, JournalCardInfoResponse> journalInfos = resolveJournalCardInfos(
                pageContent.stream().map(LikedCourseView::getJournalId).toList());
        return courseConverter.toLikedListResponse(pageContent, journalInfos, nextCursor, hasNext);
    }

    private List<LikedCourseView> fetchLikedCourses(Long memberId, CursorData cursorData, Pageable pageable) {
        if (cursorData == null) {
            return courseLikeRepository.findLikedCourses(memberId, pageable);
        }
        validateTimeCursor(cursorData);
        return courseLikeRepository.findLikedCoursesAfterCursor(
                memberId, cursorData.dateTimeValue(), cursorData.id(), pageable);
    }

    /**
     * 저장 탭 - 내가 만든 코스 목록 (최신순).
     * 본인 코스이므로 공개 여부와 무관하게 전부 보여준다.
     * 호선/역 필터는 선택 사항이고, 필터 칩 활성화에 쓸 availableLines는 최초 조회에서만 채운다.
     */
    public MyCourseListResponse getMyCourses(Long memberId, Long lineId, Long stationId,
                                             String cursor, Integer size) {
        int pageSize = resolvePageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        // 커서 해석은 한 번만 한다. 빈 문자열도 "커서 없음"으로 취급된다.
        CursorData cursorData = CursorData.decode(cursor);
        List<MyCourseView> myCourses = fetchMyCourses(memberId, lineId, stationId, cursorData, pageable);

        boolean hasNext = myCourses.size() > pageSize;
        List<MyCourseView> pageContent = hasNext ? myCourses.subList(0, pageSize) : myCourses;

        String nextCursor = null;
        if (hasNext) {
            MyCourseView last = pageContent.get(pageContent.size() - 1);
            nextCursor = new CursorData(last.getCourseId(), null, last.getCreatedAt()).encode();
        }

        // 필터 칩은 화면에 한 번만 그리므로 최초 조회에서만 계산한다 (totalCount와 같은 방식)
        List<LineView> availableLines = (cursorData == null)
                ? courseRepository.findAvailableLines(memberId)
                : List.of();

        // 카드 스탬프 모양이 여행 완료 여부에 따라 달라진다. 페이지에 실린 코스만 한 번에 확인한다.
        List<Long> courseIds = pageContent.stream().map(MyCourseView::getCourseId).toList();
        Set<Long> completedCourseIds = memberStampQueryService.getCompletedCourseIds(memberId, courseIds);

        return courseConverter.toMyListResponse(pageContent, completedCourseIds, availableLines, nextCursor, hasNext);
    }

    private List<MyCourseView> fetchMyCourses(Long memberId, Long lineId, Long stationId,
                                              CursorData cursorData, Pageable pageable) {
        if (cursorData == null) {
            return courseRepository.findMyCourses(memberId, lineId, stationId, pageable);
        }
        validateTimeCursor(cursorData);
        return courseRepository.findMyCoursesAfterCursor(
                memberId, lineId, stationId, cursorData.dateTimeValue(), cursorData.id(), pageable);
    }

    // 두 목록 모두 시간순 정렬이라 커서에는 시각과 id만 들어 있어야 한다.
    private void validateTimeCursor(CursorData cursorData) {
        if (cursorData.id() == null || cursorData.dateTimeValue() == null || cursorData.longValue() != null) {
            throw new CustomException(GlobalErrorCode.INVALID_CURSOR);
        }
    }

    private int resolvePageSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new CustomException(GlobalErrorCode.INVALID_PAGE_SIZE);
        }
        return size;
    }

    /**
     * 둘러보기 코스 목록. 노선따라 둘러보기와 코스 검색이 같은 조회를 쓴다.
     * <p>
     * 공개된 여행일지가 있는 코스만 나온다. 카드를 누르면 여행일지 상세로 가므로
     * 응답에 journalId를 함께 내린다.
     * <p>
     * 필터·검색어는 모두 선택 사항이고, 정렬은 인기순이 기본이다.
     * {@code memberId}는 하트를 채울지 판단하는 데만 쓰며 비로그인이면 null이다.
     * <p>
     * "역 선택" 드롭다운에 쓸 availableStations는 최초 조회에서만 채운다(저장 탭 availableLines와 같은 방식).
     */
    public ExploreCourseListResponse getExploreCourses(Long memberId, ExploreCourseCondition condition,
                                                       CourseSort sort, String cursor, Integer size) {
        return findExploreCourses(memberId, condition, sort, cursor, size, true);
    }

    /**
     * 컨셉 상세의 코스 목록. 조회 조건과 카드 모양은 둘러보기 목록과 같고 컨셉으로만 좁힌다.
     * <p>
     * 이 화면에는 정렬 토글만 있고 노선·역 필터가 없어 {@code availableStations}를 채우지 않는다.
     * 쓰지도 않는 후보 역 50개를 매번 실어 보내면 응답만 커지고, 프론트는 그릴 곳이 없는 목록을 받는다.
     */
    public ExploreCourseListResponse getConceptTourCourses(Long memberId, Long conceptTourId,
                                                           CourseSort sort, String cursor, Integer size) {
        return findExploreCourses(memberId, ExploreCourseCondition.ofConceptTour(conceptTourId),
                sort, cursor, size, false);
    }

    /**
     * 둘러보기 메인의 노선 섹션에 넣을 코스. 더보기부터는 목록 API가 이어받으므로 커서를 받지 않는다.
     * <p>
     * 정렬을 넘기지 않아 목록 API와 같은 기본 정렬을 쓴다. 여기만 다르게 두면 더보기로 넘어갔을 때
     * 미리보기에 있던 코스가 첫 화면에서 사라진 것처럼 보인다.
     * <p>
     * 메인 화면에는 "역 선택"이 없어 {@code availableStations}를 계산하지 않는다.
     * 계산해도 메인 응답에 담기지 않아 조회만 두 번 더 나간다.
     */
    public List<ExploreCourseResponse> getLineCourses(Long memberId, Long lineId, Integer size) {
        ExploreCourseCondition condition = new ExploreCourseCondition(lineId, null, null, null);
        return findExploreCourses(memberId, condition, null, null, size, false).courses();
    }

    private ExploreCourseListResponse findExploreCourses(Long memberId, ExploreCourseCondition condition,
                                                         CourseSort sort, String cursor, Integer size,
                                                         boolean withStationFilter) {
        int pageSize = resolvePageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1); // hasNext 판단용 1개 더 조회
        CourseSort resolvedSort = (sort == null) ? DEFAULT_SORT : sort;

        CursorData cursorData = CursorData.decode(cursor);
        List<ExploreCourseView> courses = fetchExploreCourses(condition, resolvedSort, cursorData, pageable);

        boolean hasNext = courses.size() > pageSize;
        List<ExploreCourseView> pageContent = hasNext ? courses.subList(0, pageSize) : courses;

        String nextCursor = null;
        if (hasNext) {
            nextCursor = encodeExploreCursor(pageContent.get(pageContent.size() - 1), resolvedSort);
        }

        // 드롭다운은 화면에 한 번만 그리므로 최초 조회에서만 계산한다.
        // 검색어가 있으면 검색 결과 화면이고, 그 화면에는 "역 선택"이 없어 후보 역을 싣지 않는다.
        // 노선따라 화면엔 검색바가 없고 검색 화면엔 노선 칩이 없어 두 값이 함께 오는 화면은 없다.
        boolean needsStations = withStationFilter && cursorData == null && condition.keyword() == null;
        List<StationView> availableStations = needsStations
                ? courseRepository.findDrawableStations(condition.lineId())
                : List.of();
        Set<Long> stationIdsWithCourses = needsStations
                ? toStationIds(courseRepository.findStationsWithPublicCourses(condition.lineId()))
                : Set.of();

        return courseConverter.toExploreListResponse(toExploreCards(memberId, pageContent),
                availableStations, stationIdsWithCourses, nextCursor, hasNext);
    }

    private Set<Long> toStationIds(List<StationView> stations) {
        return stations.stream().map(StationView::getStationId).collect(Collectors.toSet());
    }

    /**
     * 둘러보기 노선 칩 목록. 뽑기 역이 속한 노선 중 {@link #EXPLORE_CHIP_LINE_CODES}에 해당하는 것만
     * 내려주고, 코스가 없는 노선은 {@code hasCourses = false}로 표시한다.
     * <p>
     * 코스 없는 노선을 아예 빼면 데이터가 쌓일 때마다 칩이 늘어나 노선도가 흔들려 보인다.
     * 저장 탭의 호선 필터와 같은 방식으로, 칩은 고정해 두고 비활성 여부만 서버가 알려준다.
     * <p>
     * 활성 여부는 노선 필터와 같은 기준(역이 속한 호선 전체)으로 판정한다. 칩보다 넓은 범위가
     * 나오지만 칩에 있는 노선만 조회하므로 결과에는 영향이 없다.
     */
    public List<ExploreLineResponse> getExploreLines() {
        Set<Long> lineIdsWithCourses = courseRepository.findLinesWithPublicCourses().stream()
                .map(LineView::getLineId)
                .collect(Collectors.toSet());

        return courseRepository.findDrawableLines(EXPLORE_CHIP_LINE_CODES).stream()
                .map(line -> new ExploreLineResponse(line.getLineId(), line.getLineName(), line.getLineCode(),
                        lineIdsWithCourses.contains(line.getLineId())))
                .toList();
    }

    /**
     * 사람들이 많이 찾는 코스. 좋아요 수 내림차순으로 상위 {@value #MOST_LIKED_LIMIT}개까지만 보여준다.
     * <p>
     * 둘러보기 목록의 "인기순"(조회수 + 좋아요 × 2)과는 다른 기준이다. 화면 부제가
     * "가장 많이 담아둔 코스"라 담은 횟수만 본다.
     * <p>
     * 상한이 고정이라 커서에 정렬값 대신 다음 시작 위치를 담는다. 좋아요 수는 수시로 바뀌어서
     * 값 기준 커서를 쓰면 순위가 흔들릴 때 같은 코스가 두 번 나오거나 빠진다.
     * 30개짜리 고정 차트라 위치로 끊는 편이 단순하고 결과도 예측 가능하다.
     */
    public ExploreCourseListResponse getMostLikedCourses(Long memberId, String cursor, Integer size) {
        int pageSize = resolvePageSize(size);
        int offset = resolveOffsetCursor(cursor);

        // 상한이 30개라 전부 가져와 잘라 쓴다. 페이지마다 다시 읽어도 30행이라 부담이 없다.
        List<ExploreCourseView> topCourses =
                courseRepository.findMostLikedCourses(PageRequest.of(0, MOST_LIKED_LIMIT));

        if (offset >= topCourses.size()) {
            return courseConverter.toExploreListResponse(List.of(), List.of(), Set.of(), null, false);
        }

        int end = Math.min(offset + pageSize, topCourses.size());
        List<ExploreCourseView> pageContent = topCourses.subList(offset, end);
        boolean hasNext = end < topCourses.size();
        String nextCursor = hasNext ? new CursorData(null, (long) end, null).encode() : null;

        // 이 화면에는 "역 선택" 드롭다운이 없어 역 목록을 계산하지 않는다.
        return courseConverter.toExploreListResponse(
                toExploreCards(memberId, pageContent), List.of(), Set.of(), nextCursor, hasNext);
    }

    // 이 목록의 커서는 정렬값이 아니라 다음 시작 위치다.
    // 위치 외에 id·시각이 실려 있으면 다른 목록의 커서를 넣은 것이다. 인기순 커서를 그대로 넣으면
    // 점수(예: 322)를 위치로 읽어 조용히 빈 목록이 되므로, 모양이 다르면 400으로 막는다.
    private int resolveOffsetCursor(String cursor) {
        CursorData cursorData = CursorData.decode(cursor);
        if (cursorData == null) {
            return 0;
        }
        boolean malformed = cursorData.longValue() == null
                || cursorData.longValue() < 0
                || cursorData.id() != null
                || cursorData.dateTimeValue() != null;
        if (malformed) {
            throw new CustomException(GlobalErrorCode.INVALID_CURSOR);
        }
        return cursorData.longValue().intValue();
    }

    private List<ExploreCourseView> fetchExploreCourses(ExploreCourseCondition condition, CourseSort sort,
                                                        CursorData cursorData, Pageable pageable) {
        if (cursorData != null) {
            validateExploreCursor(cursorData, sort);
        }
        Long courseId = (cursorData == null) ? null : cursorData.id();
        LocalDateTime createdAt = (cursorData == null) ? null : cursorData.dateTimeValue();

        String keyword = normalizeKeyword(condition.keyword());
        if (sort == CourseSort.POPULAR) {
            Long score = (cursorData == null) ? null : cursorData.longValue();
            return courseRepository.findExploreCoursesByPopular(condition.lineId(), condition.stationId(),
                    keyword, condition.conceptTourId(), score, createdAt, courseId, pageable);
        }
        return courseRepository.findExploreCoursesByLatest(condition.lineId(), condition.stationId(),
                keyword, condition.conceptTourId(), createdAt, courseId, pageable);
    }

    // 커서가 정렬과 맞는지 확인한다. 인기순 커서에는 점수가 들어 있고 최신순에는 없다.
    // 정렬을 바꾸면서 이전 커서를 그대로 보내면 순서가 뒤엉키므로 아예 막는다.
    private void validateExploreCursor(CursorData cursorData, CourseSort sort) {
        boolean scoreRequired = (sort == CourseSort.POPULAR);
        boolean malformed = cursorData.id() == null
                || cursorData.dateTimeValue() == null
                || scoreRequired != (cursorData.longValue() != null);
        if (malformed) {
            throw new CustomException(GlobalErrorCode.INVALID_CURSOR);
        }
    }

    // 점수는 long으로 계산한다. 조회수·좋아요는 int라 int로 더하면 넘칠 수 있는데,
    // 같은 공식을 쓰는 쿼리 쪽은 DB가 BIGINT로 계산해서 커서 값과 비교값이 갈라진다.
    private String encodeExploreCursor(ExploreCourseView last, CourseSort sort) {
        Long score = (sort == CourseSort.POPULAR)
                ? (long) last.getViewCount() + last.getLikeCount() * 2L
                : null;
        return new CursorData(last.getCourseId(), score, last.getCreatedAt()).encode();
    }

    // 역 검색과 같은 규칙으로 다듬는다. 모든 역명이 "역"으로 끝나 꼬리의 "역"은 역을 구분하지 못한다.
    // "역" 한 글자는 떼지 않는다. 역삼역처럼 이름에 "역"이 든 역을 찾으려는 입력이다.
    // 접미사를 뗀 뒤 이스케이프한다. 순서를 바꾸면 이스케이프 문자가 붙어 꼬리 판정이 어긋난다.
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > STATION_NAME_SUFFIX.length() && trimmed.endsWith(STATION_NAME_SUFFIX)) {
            trimmed = trimmed.substring(0, trimmed.length() - STATION_NAME_SUFFIX.length());
        }
        return escapeLikePattern(trimmed);
    }

    // 검색어에 든 LIKE 와일드카드를 문자 그대로 취급한다.
    // 이스케이프하지 않으면 "%" 한 글자로 공개 코스 전체가 조회된다.
    private String escapeLikePattern(String keyword) {
        return keyword.replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }

    // 카드에 필요한 태그와 좋아요 여부를 페이지 단위로 한 번에 채운다 (코스마다 조회하면 N+1).
    private List<ExploreCourseResponse> toExploreCards(Long memberId, List<ExploreCourseView> courses) {
        if (courses.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = courses.stream().map(ExploreCourseView::getCourseId).toList();
        Map<Long, List<Long>> placeIdsByCourse = groupPlaceIdsByCourse(courseIds);
        Map<Long, List<String>> tagsByCourse = resolveTagsByCourse(placeIdsByCourse);
        Set<Long> likedCourseIds = resolveLikedCourseIds(memberId, courseIds);

        // 카드 배경은 작성자가 여행일지에 올린 첫 사진이다.
        Map<Long, JournalCardInfoResponse> journalInfos = resolveJournalCardInfos(
                courses.stream().map(ExploreCourseView::getJournalId).toList());

        return courses.stream()
                .map(course -> courseConverter.toExploreCourseResponse(
                        course,
                        tagsByCourse.getOrDefault(course.getCourseId(), List.of()),
                        likedCourseIds.contains(course.getCourseId()),
                        resolveJournalImageUrl(journalInfos, course.getJournalId())))
                .toList();
    }

    /**
     * 코스별 대표 태그를 한 번에 집계한다.
     * <p>
     * 장소 태그를 페이지 전체에 대해 한 번만 조회하고, 코스마다 담긴 장소들의 태그를 세서
     * 많이 나온 순으로 자른다. 코스별로 태그 조회를 부르면 페이지 크기만큼 쿼리가 나간다.
     */
    private Map<Long, List<String>> resolveTagsByCourse(Map<Long, List<Long>> placeIdsByCourse) {
        List<Long> allPlaceIds = placeIdsByCourse.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
        Map<Long, List<String>> tagsByPlace = placeInfoQueryService.getTagNamesByPlace(allPlaceIds);

        Map<Long, List<String>> result = new LinkedHashMap<>();
        placeIdsByCourse.forEach((courseId, placeIds) -> {
            Map<String, Long> tagCounts = placeIds.stream()
                    .flatMap(placeId -> tagsByPlace.getOrDefault(placeId, List.of()).stream())
                    .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));
            result.put(courseId, tagCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(TAGS_PER_CARD)
                    .map(Map.Entry::getKey)
                    .toList());
        });
        return result;
    }

    /**
     * 카드에 얹을 여행일지 정보(대표 사진·소요시간)를 journalId 기준으로 모은다.
     * <p>
     * 카드마다 조회하면 카드 수만큼 쿼리가 나가므로 페이지 단위로 한 번에 받는다.
     * 일지가 없거나 미작성이면 값이 빠진다. 둘러보기·장소별 코스는 공개 일지가 있는 코스만
     * 노출하므로 실제로는 항상 채워지지만, 호출부가 null을 견디도록 두었다.
     */
    private Map<Long, JournalCardInfoResponse> resolveJournalCardInfos(List<Long> journalIds) {
        return journalCardQueryService.getJournalCourseCardInfos(journalIds);
    }
    private String resolveJournalImageUrl(Map<Long, JournalCardInfoResponse> journalInfos, Long journalId) {
        JournalCardInfoResponse info = journalInfos.get(journalId);
        return (info == null) ? null : info.imageUrl();
    }


    private Set<Long> resolveLikedCourseIds(Long memberId, List<Long> courseIds) {
        if (memberId == null || courseIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(courseLikeRepository.findLikedCourseIds(memberId, courseIds));
    }

    /**
     * 저장 탭에서 "코스 확인"을 눌렀을 때의 화면. 지도와 코스 순서를 그린다.
     * <p>
     * 코스 상세({@code GET /courses/{courseId}})와는 다른 화면이라 응답도 다르다.
     * 여기는 지도 핀을 찍을 좌표가 필요한 대신 조회수·좋아요·여행일지 내용이 필요 없다.
     * 조회수도 올리지 않는다. 내 코스를 관리하는 화면이라 드나들 때마다 오르면 인기순이 왜곡된다.
     * <p>
     * 본인 코스만 볼 수 있고, 남의 코스는 존재 여부도 알리지 않도록 404로 응답한다.
     * 공개 조건은 걸지 않는다. 일지를 아직 안 썼거나 비공개인 코스도 본인에게는 보여야 한다.
     */
    public MyCourseDetailResponse getMyCourseDetail(Long memberId, Long courseId) {
        CourseDetailView course = courseRepository.findMyCourseDetail(memberId, courseId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_NOT_FOUND));

        List<CoursePlace> coursePlaces = coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(courseId);
        return courseConverter.toMyCourseDetailResponse(course, resolveCoursePlaces(coursePlaces));
    }

    /**
     * "내 코스로 만들기" 화면. 타인의 공개 코스를 가져오기 직전에 구성을 확인하는 단계다.
     * <p>
     * 여행일지 상세와 같은 코스를 보지만, 이 화면에는 사진·후기·작성자·태그가 없고
     * 지도와 순서 목록만 있어 코스 구성만 내린다.
     * <p>
     * 비공개이거나 없는 코스는 404다(복제와 같은 조건). 본인 코스인지는 보지 않는다 —
     * 조회는 부작용이 없고, 진입 자체는 프론트가 {@code isMine}으로 막는다.
     */
    public CourseCopyPreviewResponse getCourseCopyPreview(Long courseId) {
        CourseDetailView course = courseRepository.findPublicCourseDetail(courseId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_NOT_FOUND));

        List<CoursePlace> coursePlaces = coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(courseId);
        return courseConverter.toCourseCopyPreviewResponse(course, resolveCoursePlaces(coursePlaces));
    }

    /**
     * 공유 링크로 들어왔을 때의 코스 확인 화면. {@code MyCourseDetailResponse}와 같은 화면 구성이지만
     * 소유자·로그인 여부를 따지지 않는다. 대신 courseId가 아닌 추측 불가능한
     * shareToken으로만 조회해, 다른 사람이 courseId를 바꿔가며 남의 코스를 열람할 수 없게 한다.
     */
    public CourseShareResponse getCourseShareDetail(String shareToken) {
        CourseDetailView course = courseRepository.findShareCourseDetail(shareToken)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_NOT_FOUND));

        List<CoursePlace> coursePlaces = coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(course.getCourseId());
        return courseConverter.toCourseShareResponse(course, resolveCoursePlaces(coursePlaces));
    }

    // 코스에 담긴 장소를 순서대로 채운다.
    // 장소 조회 결과는 요청 순서를 보장하지 않아 id로 묶은 뒤 order_num 순으로 다시 세운다.
    private List<CoursePlaceDetailResponse> resolveCoursePlaces(List<CoursePlace> coursePlaces) {
        if (coursePlaces.isEmpty()) {
            return List.of();
        }

        List<Long> placeIds = coursePlaces.stream().map(CoursePlace::getPlaceId).toList();
        Map<Long, HistoricalPlaceInfoResponse> placeById = placeInfoQueryService.getHistoricalPlaceInfos(placeIds).stream()
                .collect(Collectors.toMap(HistoricalPlaceInfoResponse::placeId, place -> place));

        // 장소가 실제로 사라진 경우만 누락으로 본다. 반려·삭제는 historical 조회가 상태와 함께 반환한다.
        if (placeById.size() != coursePlaces.size()) {
            List<Long> missingPlaceIds = placeIds.stream()
                    .filter(placeId -> !placeById.containsKey(placeId))
                    .toList();
            log.warn("코스에 담긴 장소를 찾을 수 없어 응답에서 제외: courseId={}, missingPlaceIds={}",
                    coursePlaces.get(0).getCourseId(), missingPlaceIds);
        }

        // 실제로 삭제된 레코드는 지도 핀과 목록을 채울 정보가 없어 제외한다.
        return coursePlaces.stream()
                .filter(coursePlace -> placeById.containsKey(coursePlace.getPlaceId()))
                .map(coursePlace -> courseConverter.toCoursePlaceDetailResponse(
                        placeById.get(coursePlace.getPlaceId()), coursePlace.getOrderNum()))
                .toList();
    }

    /**
     * 장소 상세 화면 하단의 "이 장소를 포함한 코스".
     * 인기순 상위 6개를 뽑되 노출 순서는 매번 섞는다. 같은 장소를 다시 열었을 때
     * 늘 같은 순서면 아래쪽 코스가 계속 묻히기 때문이다.
     */
    public List<PlaceCourseResponse> getCoursesByPlace(Long placeId) {
        List<PlaceCourseView> courses = new ArrayList<>(courseRepository.findPopularPublicCoursesByPlaceId(
                placeId, PageRequest.of(0, PLACE_COURSE_LIMIT)));
        if (courses.isEmpty()) {
            return List.of();
        }
        Collections.shuffle(courses);

        List<Long> courseIds = courses.stream().map(PlaceCourseView::getCourseId).toList();
        Map<Long, List<Long>> placeIdsByCourse = groupPlaceIdsByCourse(courseIds);
        Map<Long, String> placeImageByCourse = resolveCoverImages(placeIdsByCourse);

        // 카드 배경과 소요시간은 여행일지 값을 먼저 쓴다.
        // 사진이 없으면 첫 장소 사진으로, 소요시간이 없으면 장소 수 추정으로 물러난다.
        Map<Long, JournalCardInfoResponse> journalInfos = resolveJournalCardInfos(
                courses.stream().map(PlaceCourseView::getJournalId).toList());

        return courses.stream()
                .map(course -> {
                    List<Long> placeIds = placeIdsByCourse.getOrDefault(course.getCourseId(), List.of());
                    JournalCardInfoResponse journalInfo = journalInfos.get(course.getJournalId());
                    String imageUrl = (journalInfo != null && journalInfo.imageUrl() != null)
                            ? journalInfo.imageUrl()
                            : placeImageByCourse.get(course.getCourseId());
                    TravelDuration travelDuration = (journalInfo == null) ? null : journalInfo.travelDuration();
                    return courseConverter.toPlaceCourseResponse(
                            course, placeIds.size(), resolveCourseTags(placeIds), imageUrl, travelDuration);
                })
                .toList();
    }

    // 코스별 장소 id를 순서대로 묶는다. 장소 수·대표 이미지·태그가 모두 여기서 나온다.
    private Map<Long, List<Long>> groupPlaceIdsByCourse(List<Long> courseIds) {
        return coursePlaceRepository.findByCourseIdInOrderByCourseIdAscOrderNumAsc(courseIds).stream()
                .collect(Collectors.groupingBy(
                        CoursePlace::getCourseId,
                        LinkedHashMap::new,
                        Collectors.mapping(CoursePlace::getPlaceId, Collectors.toList())
                ));
    }

    // 공개 코스 카드의 배경은 승인된 첫 장소 이미지만 사용한다.
    private Map<Long, String> resolveCoverImages(Map<Long, List<Long>> placeIdsByCourse) {
        Map<Long, Long> firstPlaceByCourse = new LinkedHashMap<>();
        placeIdsByCourse.forEach((courseId, placeIds) -> {
            if (!placeIds.isEmpty()) {
                firstPlaceByCourse.put(courseId, placeIds.get(0));
            }
        });
        if (firstPlaceByCourse.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> imageUrlByPlace = placeInfoQueryService.getPlaceInfos(List.copyOf(firstPlaceByCourse.values()))
                .stream()
                .filter(place -> place.imageUrl() != null)
                .collect(Collectors.toMap(PlaceInfoResponse::placeId, PlaceInfoResponse::imageUrl));

        Map<Long, String> result = new LinkedHashMap<>();
        firstPlaceByCourse.forEach((courseId, placeId) -> result.put(courseId, imageUrlByPlace.get(placeId)));
        return result;
    }

    // 코스 대표 태그 = 그 코스 장소들의 태그를 집계한 상위 태그.
    // 집계는 장소 도메인이 맡고 있어 코스마다 한 번씩 호출한다(코스가 6개로 고정이라 호출 수도 고정).
    private List<String> resolveCourseTags(List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        return placeInfoQueryService.getTopTagNames(placeIds).stream()
                .limit(TAGS_PER_CARD)
                .toList();
    }

    // 다른 회원 프로필의 공개 코스 개수. 호출부(회원 조회)에서 이미 존재 검증을 마쳤다고 가정한다.
    public long countPublicCourses(Long memberId) {
        return courseRepository.countPublicCoursesByMemberId(memberId);
    }

    /**
     * 다른 회원 프로필의 공개코스 탭. 그 회원이 만든 코스 중 여행일지가 공개된 코스만 최신순으로 조회한다.
     * <p>
     * 저장 탭(getMyCourses)과 달리 호선/역 필터가 없어 availableLines를 계산하지 않는다.
     * 프로필 조회와 달리 독립된 API라 여기서 직접 회원 존재를 검증한다.
     */
    public MemberCourseListResponse getMemberPublicCourses(Long memberId, String cursor, Integer size) {
        if (!memberExistenceQueryService.existsMember(memberId)) {
            log.warn("존재하지 않는 회원의 공개코스 탭 조회 시도: memberId={}", memberId);
            throw new CustomException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        int pageSize = resolvePageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1); // hasNext 판단용 1개 더 조회

        CursorData cursorData = CursorData.decode(cursor);
        List<MemberCourseCardView> courses = fetchMemberPublicCourses(memberId, cursorData, pageable);

        boolean hasNext = courses.size() > pageSize;
        List<MemberCourseCardView> pageContent = hasNext ? courses.subList(0, pageSize) : courses;

        String nextCursor = null;
        if (hasNext) {
            MemberCourseCardView last = pageContent.get(pageContent.size() - 1);
            nextCursor = new CursorData(last.getCourseId(), null, last.getCreatedAt()).encode();
        }

        List<MemberCourseCardResponse> cards = toMemberCourseCards(pageContent);

        return courseConverter.toMemberCourseListResponse(cards, nextCursor, hasNext);
    }

    private List<MemberCourseCardView> fetchMemberPublicCourses(Long memberId, CursorData cursorData, Pageable pageable) {
        if (cursorData == null) {
            return courseRepository.findPublicCoursesByMemberId(memberId, pageable);
        }
        validateTimeCursor(cursorData);
        return courseRepository.findPublicCoursesByMemberIdAfterCursor(
                memberId, cursorData.dateTimeValue(), cursorData.id(), pageable);
    }

    // journalId를 모아 한 번에 imageUrl을 조회한다(코스마다 조회하면 N+1).
    private List<MemberCourseCardResponse> toMemberCourseCards(List<MemberCourseCardView> courses) {
        Map<Long, JournalCardInfoResponse> journalInfos = resolveJournalCardInfos(
                courses.stream().map(MemberCourseCardView::getJournalId).toList());

        return courses.stream()
                .map(course -> courseConverter.toMemberCourseCardResponse(
                        course, resolveJournalImageUrl(journalInfos, course.getJournalId())))
                .toList();
    }

    public CourseInfoResponse getCourseInfo(Long courseId) {
        return courseConverter.toInfoResponse(findCourse(courseId));
    }

    /**
     * 이 코스에 좋아요(하트)를 눌러 뒀는지. 코스 상세 화면의 하트를 채울지 판단하는 데 쓴다.
     * <p>
     * 비로그인({@code memberId}가 null)이면 누를 사람이 없으므로 항상 false다.
     */
    public boolean isLikedByMember(Long courseId, Long memberId) {
        if (memberId == null) {
            return false;
        }
        return courseLikeRepository.existsByMemberIdAndCourseId(memberId, courseId);
    }

    public List<CoursePlaceInfoResponse> getCoursePlaces(Long courseId) {
        findCourse(courseId);
        return courseConverter.toPlaceInfoResponses(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(courseId));
    }

    // 역별 인기 코스 상위 limit개
    // 공개된 여행일지가 있는 코스만 노출한다
    // 스탬프 페이지·둘러보기 등 다른 도메인이 Course에 직접 의존하지 않고 이 메서드를 호출한다
    // 카드 제목은 journal.title을 쓴다 — 소비하는 도메인(스탬프)에 영향 있음, 공유 필요.
    public List<PopularCourseResponse> getPopularCoursesByStation(Long stationId, int limit) {
        return getPopularCoursesByStation(stationId, limit, null);
    }

    // memberId를 넘기면 응답의 isLiked가 채워진다. null이면 전부 false.
    public List<PopularCourseResponse> getPopularCoursesByStation(Long stationId, int limit, Long memberId) {
        List<PopularCourseView> courses = courseRepository.findPopularPublicCoursesByStationId(stationId, PageRequest.of(0, limit));
        return courseConverter.toPopularResponses(courses, resolveLikedCourses(memberId, courses));
    }

    // 조회한 코스들의 좋아요 여부를 한 번에 조회한다 (코스마다 조회하면 N+1)
    private Set<Long> resolveLikedCourses(Long memberId, List<PopularCourseView> courses) {
        return resolveLikedCourseIds(memberId, courses.stream().map(PopularCourseView::getCourseId).toList());
    }

    private Course findCourse(Long courseId) {
        // 삭제된 코스는 Course의 @SQLRestriction 으로 조회에서 자동 제외된다
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_NOT_FOUND));
    }
}
