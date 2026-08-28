package com.cotato.nextstation.domain.journal.service.query;

import com.cotato.nextstation.domain.course.dto.response.CoursePlaceInfoResponse;
import com.cotato.nextstation.domain.course.entity.CoursePlace;
import com.cotato.nextstation.domain.course.repository.CoursePlaceRepository;
import com.cotato.nextstation.domain.course.service.command.CourseCommandService;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.domain.journal.converter.JournalConverter;
import com.cotato.nextstation.domain.journal.dto.response.JournalDetailResponse;
import com.cotato.nextstation.domain.journal.dto.response.JournalWriteInfoResponse;
import com.cotato.nextstation.domain.journal.dto.response.MyJournalListResponse;
import com.cotato.nextstation.domain.journal.dto.response.UncompletedJournalListResponse;
import com.cotato.nextstation.domain.journal.entity.Journal;
import com.cotato.nextstation.domain.journal.entity.JournalImage;
import com.cotato.nextstation.domain.journal.enums.TravelDuration;
import com.cotato.nextstation.domain.journal.exception.JournalErrorCode;
import com.cotato.nextstation.domain.member.entity.MemberStatus;
import com.cotato.nextstation.domain.journal.repository.JournalImageRepository;
import com.cotato.nextstation.domain.journal.repository.JournalImageRepository.JournalImageView;
import com.cotato.nextstation.domain.journal.repository.JournalRepository;
import com.cotato.nextstation.domain.journal.repository.JournalRepository.CourseSnapshotView;
import com.cotato.nextstation.domain.journal.repository.JournalRepository.MyJournalCardView;
import com.cotato.nextstation.domain.journal.repository.JournalRepository.UncompletedCourseCardView;
import com.cotato.nextstation.domain.place.dto.response.PlaceInfoResponse;
import com.cotato.nextstation.domain.place.entity.PlaceReview;
import com.cotato.nextstation.domain.place.entity.PlaceReviewImage;
import com.cotato.nextstation.domain.place.repository.PlaceReviewImageRepository;
import com.cotato.nextstation.domain.place.repository.PlaceReviewRepository;
import com.cotato.nextstation.domain.place.service.query.PlaceInfoQueryService;
import com.cotato.nextstation.domain.stamp.entity.MemberStamp;
import com.cotato.nextstation.domain.stamp.service.query.MemberStampQueryService;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import com.cotato.nextstation.domain.station.service.query.StationQueryService;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.util.CursorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JournalQueryService {

    private static final int TAGS_PER_CARD = 2;

    // 내 여행일지 목록 페이지 크기. 저장 탭 "내가 만든 코스"와 같은 기본값·상한을 쓴다.
    private static final int MY_JOURNALS_DEFAULT_SIZE = 10;
    private static final int MY_JOURNALS_MAX_SIZE = 50;

    private final MemberStampQueryService memberStampQueryService;
    private final CourseQueryService courseQueryService;
    private final CourseCommandService courseCommandService;
    private final CoursePlaceRepository coursePlaceRepository;
    private final PlaceInfoQueryService placeInfoQueryService;
    private final StationQueryService stationQueryService;

    private final JournalRepository journalRepository;
    private final JournalImageRepository journalImageRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final PlaceReviewImageRepository placeReviewImageRepository;

    private final JournalConverter journalConverter;


    // 여행일지 작성 초기 정보 조회
    public JournalWriteInfoResponse getWriteInfo(Long memberId, Long memberStampId) {
        // 1. memberStampId → courseId
        Long courseId = memberStampQueryService.getCourseId(memberId, memberStampId);

        // 2. courseId → 코스 정보 (courseName, stationId). 완주 후 코스가 삭제됐어도 작성은
        // 계속할 수 있어야 하므로 삭제 여부와 무관하게 조회한다 (getCourseSnapshot 참고)
        CourseSnapshotView courseSnapshot = getCourseSnapshot(courseId);

        // 3. stationId → stationName
        String stationName = stationQueryService.getStationName(courseSnapshot.getStationId());

        // 4. courseId → 장소 목록 (placeId + orderNum). CoursePlace는 코스가 삭제돼도 남아 있다.
        List<CoursePlaceInfoResponse> coursePlaces = journalConverter.toPlaceInfoResponses(
                coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(courseId));
        List<Long> placeIds = coursePlaces.stream()
                .map(CoursePlaceInfoResponse::placeId)
                .toList();

        // 5. placeIds → 장소 이름
        Map<Long, PlaceInfoResponse> placeInfoMap = placeInfoQueryService.getPlaceInfos(placeIds)
                .stream()
                .collect(Collectors.toMap(PlaceInfoResponse::placeId, Function.identity()));

        // 6. placeIds → 태그 상위 3개
        List<String> tags = placeInfoQueryService.getTopTagNames(placeIds);

        return journalConverter.toWriteInfoResponse(
                stationName, courseSnapshot.getName(), tags, coursePlaces, placeInfoMap);
    }

    // courseQueryService.getCourseInfo()는 Course의 @SQLRestriction(is_deleted = false) 때문에
    // 완주 후 코스가 삭제되면 COURSE_NOT_FOUND를 던진다. 여행일지 조회(작성 초기 정보/미작성 목록/상세)는
    // 코스 삭제와 무관하게 완주 당시 코스 정보를 그대로 보여줘야 해서, journalRepository가 course
    // 테이블을 직접 조회해 그 제약을 우회한다.
    // Course 도메인 클래스에 의존하지 않도록 예외는 JournalErrorCode의 로컬 상수로 던진다
    // (courseId는 이미 소유권 검증을 마친 스탬프에서 나온 값이라 실제로는 거의 발생하지 않는다).
    private CourseSnapshotView getCourseSnapshot(Long courseId) {
        return journalRepository.findCourseSnapshotById(courseId)
                .orElseThrow(() -> new CustomException(JournalErrorCode.COURSE_NOT_FOUND));
    }

    // 여행일지 미작성 코스 조회
    public UncompletedJournalListResponse getUncompletedJournals(Long memberId) {
        // 1. 이미 일지가 작성된 memberStampId 목록 (journal 도메인 내부에서 처리)
        Set<Long> completedStampIds = journalRepository.findCompletedMemberStampIdsByMemberId(memberId);

        // 2. 미작성 스탬프 목록 조회 (최신순)
        List<MemberStamp> uncompletedStamps =
                memberStampQueryService.getUncompletedStamps(memberId, completedStampIds);

        if (uncompletedStamps.isEmpty()) {
            return new UncompletedJournalListResponse(0, List.of());
        }

        // 3. courseId 목록 추출
        List<Long> courseIds = uncompletedStamps.stream()
                .map(MemberStamp::getCourseId)
                .toList();

        // 4. courseId → 코스 카드 정보 (courseName, stationName, line). 완주 당시 스탬프라 코스가
        // 이후 삭제됐을 수 있어 삭제 여부와 무관하게 한 번에 조회한다(스탬프 하나가 미작성이라도
        // 전체 목록이 죽지 않도록 개별 조회 대신 배치 조회를 쓴다). course→station→line을 한
        // 쿼리에서 조인해 가져오므로 stationQueryService는 따로 부르지 않는다.
        Map<Long, UncompletedCourseCardView> courseCardMap = journalRepository
                .findUncompletedCourseCardsByIds(courseIds)
                .stream()
                .collect(Collectors.toMap(UncompletedCourseCardView::getCourseId, Function.identity()));

        // course→station INNER JOIN이라 course나 station이 하드 삭제되면 그 courseId는 결과에서
        // 통째로 빠진다. 그 스탬프만 목록에서 제외하고, 나머지 스탬프는 정상 응답돼야 한다
        // (배치 조회를 쓰는 목적 자체가 스탬프 하나 때문에 전체가 죽지 않게 하는 것이다).
        List<MemberStamp> validStamps = uncompletedStamps.stream()
                .filter(stamp -> courseCardMap.containsKey(stamp.getCourseId()))
                .toList();
        if (validStamps.size() < uncompletedStamps.size()) {
            log.warn("코스 카드를 찾을 수 없어 미작성 목록에서 제외: missingCourseIds={}",
                    uncompletedStamps.stream()
                            .map(MemberStamp::getCourseId)
                            .filter(courseId -> !courseCardMap.containsKey(courseId))
                            .toList());
        }

        // 5. courseId → placeIds → 태그 2개. CoursePlace는 코스가 삭제돼도 남아 있다.
        // TODO: CoursePlaceRepository에 배치 조회 메서드 생기면 N+1 개선 가능
        Map<Long, List<String>> tagsByCourse = new HashMap<>();
        for (MemberStamp stamp : validStamps) {
            Long courseId = stamp.getCourseId();
            List<Long> placeIds = coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(courseId).stream()
                    .map(CoursePlace::getPlaceId)
                    .toList();
            List<String> tags = placeInfoQueryService.getTopTagNames(placeIds).stream()
                    .limit(TAGS_PER_CARD)
                    .toList();
            tagsByCourse.put(courseId, tags);
        }

        return journalConverter.toUncompletedJournalListResponse(validStamps, courseCardMap, tagsByCourse);
    }


    // 내 여행일지 목록 조회 (최신순, 커서 기반 페이지네이션)
    public MyJournalListResponse getMyJournals(Long memberId, String cursor, Integer size) {
        int pageSize = resolveMyJournalsPageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1); // hasNext 판단용 1개 더 조회

        CursorData cursorData = CursorData.decode(cursor);
        List<MyJournalCardView> myJournalCards = fetchMyJournalCards(memberId, cursorData, pageable);

        boolean hasNext = myJournalCards.size() > pageSize;
        List<MyJournalCardView> pageContent = hasNext ? myJournalCards.subList(0, pageSize) : myJournalCards;

        String nextCursor = null;
        if (hasNext) {
            MyJournalCardView last = pageContent.get(pageContent.size() - 1);
            nextCursor = new CursorData(last.getJournalId(), null, last.getCreatedAt()).encode();
        }

        if (pageContent.isEmpty()) {
            return journalConverter.toMyJournalListResponse(List.of(), Map.of(), nextCursor, hasNext);
        }

        List<Long> journalIds = pageContent.stream().map(MyJournalCardView::getJournalId).toList();
        Map<Long, String> thumbnailUrlByJournalId = journalImageRepository.findImagesByJournalIds(journalIds).stream()
                .collect(Collectors.toMap(
                        JournalImageView::getJournalId,
                        JournalImageView::getImageUrl,
                        (first, next) -> first));

        return journalConverter.toMyJournalListResponse(pageContent, thumbnailUrlByJournalId, nextCursor, hasNext);
    }

    private List<MyJournalCardView> fetchMyJournalCards(Long memberId, CursorData cursorData, Pageable pageable) {
        if (cursorData == null) {
            return journalRepository.findMyJournalCards(memberId, pageable);
        }
        validateMyJournalsCursor(cursorData);
        return journalRepository.findMyJournalCardsAfterCursor(
                memberId, cursorData.dateTimeValue(), cursorData.id(), pageable);
    }

    // 이 목록은 시간순 정렬이라 커서에는 시각과 id만 들어 있어야 한다.
    private void validateMyJournalsCursor(CursorData cursorData) {
        if (cursorData.id() == null || cursorData.dateTimeValue() == null || cursorData.longValue() != null) {
            throw new CustomException(GlobalErrorCode.INVALID_CURSOR);
        }
    }

    private int resolveMyJournalsPageSize(Integer size) {
        if (size == null) {
            return MY_JOURNALS_DEFAULT_SIZE;
        }
        if (size < 1 || size > MY_JOURNALS_MAX_SIZE) {
            throw new CustomException(GlobalErrorCode.INVALID_PAGE_SIZE);
        }
        return size;
    }

    // 여행일지 상세 조회
    public JournalDetailResponse getJournalDetail(Long memberId, Long journalId) {
        // 1. 여행일지 조회
        Journal journal = journalRepository.findById(journalId)
                .orElseThrow(() -> new CustomException(JournalErrorCode.JOURNAL_NOT_FOUND));

        // 2. 본인 여부 확인 → 타인이면 공개 일지만 조회 가능
        boolean isOwner = journal.getMember().getId().equals(memberId);
        if (!isOwner && !journal.isPublic()) {
            throw new CustomException(JournalErrorCode.JOURNAL_FORBIDDEN);
        }

        // 2-1. 작성자가 탈퇴(WITHDRAWN)했으면 타인에게는 노출하지 않는다. 코스 목록 쪽은
        // NOT_WITHDRAWN으로 걸러지지만, journalId를 직접 아는 경우(북마크·공유 링크 등)
        // 목록을 거치지 않고 상세로 바로 들어올 수 있어 여기서도 방어한다.
        if (!isOwner && journal.getMember().getStatus() == MemberStatus.WITHDRAWN) {
            throw new CustomException(JournalErrorCode.JOURNAL_FORBIDDEN);
        }

        // 3. memberStampId → courseId
        Long courseId = memberStampQueryService.getCourseId(
                journal.getMember().getId(), journal.getMemberStampId());

        // 4. courseId → 코스 정보 (courseName, stationId, viewCount, saveCount). 이미 작성된
        // 일지는 코스가 이후 삭제돼도 상세 조회가 계속 가능해야 하므로 삭제 여부와 무관하게 조회한다
        CourseSnapshotView courseSnapshot = getCourseSnapshot(courseId);

        // 5. stationId → stationName, line
        String stationName = stationQueryService.getStationName(courseSnapshot.getStationId());
        LineSummaryResponse line = stationQueryService.getLine(courseSnapshot.getStationId());

        // 6. courseId → 장소 목록 (placeId + orderNum). CoursePlace는 코스가 삭제돼도 남아 있다.
        List<CoursePlaceInfoResponse> coursePlaces = journalConverter.toPlaceInfoResponses(
                coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(courseId));
        List<Long> placeIds = coursePlaces.stream()
                .map(CoursePlaceInfoResponse::placeId)
                .toList();

        // 7. placeIds → 장소 이름
        Map<Long, PlaceInfoResponse> placeInfoMap = placeInfoQueryService.getPlaceInfos(placeIds)
                .stream()
                .collect(Collectors.toMap(PlaceInfoResponse::placeId, Function.identity()));

        // 8. placeIds → 태그 상위 3개
        List<String> tags = placeInfoQueryService.getTopTagNames(placeIds);

        // 9. journalId → 대표 사진 + 서브 사진. PATCH 수정 시 journalPhotos[].photoId로 KEEP/DELETE
        // 대상을 특정해야 해서, imageUrl만 뽑지 않고 엔티티(id 포함)를 그대로 컨버터에 넘긴다.
        List<JournalImage> journalImages = journalImageRepository.findByJournalIdOrderByIdAsc(journalId);

        // 10. journalId → 장소 리뷰 + 리뷰 이미지
        List<PlaceReview> placeReviews = placeReviewRepository.findByJournalId(journalId);
        Map<Long, PlaceReview> reviewByPlaceId = placeReviews.stream()
                .collect(Collectors.toMap(pr -> pr.getPlace().getId(), Function.identity()));

        List<Long> reviewIds = placeReviews.stream().map(PlaceReview::getId).toList();
        Map<Long, String> imageUrlByReviewId = placeReviewImageRepository
                .findByPlaceReviewIdIn(reviewIds).stream()
                .collect(Collectors.toMap(
                        pri -> pri.getPlaceReview().getId(),
                        PlaceReviewImage::getImageUrl,
                        (a, b) -> a  // 기획상 1개만이라 중복 시 첫 번째 사용
                ));

        // 11. 조회수 반영 (본인 조회는 CourseCommandService 내부에서 제외) + 좋아요 여부
        //
        // increaseViewCount가 반환하는 값을 그대로 써야 한다. 이 트랜잭션(readOnly)에서 courseSnapshot을
        // 다시 조회해도 REPEATABLE READ 스냅샷 때문에 증가 전 값이 보인다 — REQUIRES_NEW로 증가를 수행한
        // 그 트랜잭션 안에서 읽은 값만 증가분을 반영하고 있다. 실패(코스가 삭제됐거나 본인 조회로 no-op)했으면
        // null이 오므로 4번에서 조회해 둔 courseSnapshot의 값으로 대체한다.
        Integer updatedViewCount = courseCommandService.increaseViewCount(courseId, memberId);
        boolean isLiked = courseQueryService.isLikedByMember(courseId, memberId);
        int viewCount = updatedViewCount != null ? updatedViewCount : courseSnapshot.getViewCount();

        return journalConverter.toJournalDetailResponse(
                journal, line, stationName, courseSnapshot, viewCount, isOwner, isLiked, tags, journalImages,
                coursePlaces, placeInfoMap, reviewByPlaceId, imageUrlByReviewId);
    }

    // Course 도메인이 코스 카드의 소요시간(travel_duration) 표시를 위함
    public TravelDuration getTravelDuration(Long journalId) {
        // course.journal_id가 nullable이라 journalId가 null로 들어올 수 있다. findById(null)은 예외라 먼저 막는다.
        if (journalId == null) {
            return null;
        }
        return journalRepository.findById(journalId)
                .map(Journal::getTravelDuration)
                .orElse(null);  // 일지 없거나 미작성이면 null (장소 수로 추정)
    }






}