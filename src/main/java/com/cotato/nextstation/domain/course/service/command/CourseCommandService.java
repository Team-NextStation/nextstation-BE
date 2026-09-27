package com.cotato.nextstation.domain.course.service.command;

import com.cotato.nextstation.domain.course.converter.CourseConverter;
import com.cotato.nextstation.domain.course.dto.request.CourseCopyRequest;
import com.cotato.nextstation.domain.course.dto.request.CourseCreateRequest;
import com.cotato.nextstation.domain.course.dto.request.CourseUpdateRequest;
import com.cotato.nextstation.domain.course.dto.response.CourseCreateResponse;
import com.cotato.nextstation.domain.course.dto.response.CourseUpdateResponse;
import com.cotato.nextstation.domain.course.entity.Course;
import com.cotato.nextstation.domain.course.entity.CoursePlace;
import com.cotato.nextstation.domain.course.exception.CourseErrorCode;
import com.cotato.nextstation.domain.course.repository.CoursePlaceRepository;
import com.cotato.nextstation.domain.course.repository.CourseRepository;
import com.cotato.nextstation.domain.station.entity.Station;
import com.cotato.nextstation.domain.station.repository.StationRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.util.ProfanityFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CourseCommandService {

    private final CourseRepository courseRepository;
    private final CoursePlaceRepository coursePlaceRepository;
    private final CourseConverter courseConverter;
    private final CourseViewCountUpdater courseViewCountUpdater;
    private final StationRepository stationRepository;
    private final ProfanityFilter profanityFilter;

    /**
     * 코스 상세를 열었을 때 조회수를 올린다. 다른 도메인이 코스 상세 화면을 그릴 때 호출한다.
     * <p>
     * 본인이 자기 코스를 연 경우는 올리지 않는다. {@code viewerMemberId}가 null이면 비로그인 조회다.
     * <p>
     * 증가에 실패해도 조회 응답은 그대로 나가야 하므로 예외를 삼킨다. 조회수는 부가 정보인데
     * 잠금 경합 같은 이유로 실패했다고 화면 전체가 안 뜨면 손해가 크다.
     * <p>
     * 증가된 최신 조회수를 반환한다({@code null}이면 실패했거나 본인 조회라 증가하지 않은 경우).
     * 호출부가 이미 들고 있는 코스 정보를 이 값으로 갈아끼워야 응답에 증가분이 반영된다 —
     * 호출부의 트랜잭션에서 코스를 다시 조회해도 REPEATABLE READ 스냅샷 때문에 증가 전 값이 보인다.
     */
    public Integer increaseViewCount(Long courseId, Long viewerMemberId) {
        try {
            return courseViewCountUpdater.increaseViewCount(courseId, viewerMemberId);
        } catch (Exception e) {
            log.warn("조회수 증가 실패: courseId={}, viewerMemberId={}", courseId, viewerMemberId, e);
            return null;
        }
    }

    /**
     * 여행일지를 코스에 연결한다. 일지를 작성할 때 여행일지 도메인이 호출한다.
     * <p>
     * 코스는 이 값으로 공개 여부를 판정한다(둘러보기·검색·좋아요·복제). 연결되지 않으면
     * 공개 일지를 써도 그 코스는 어느 목록에도 나오지 않는다.
     * <p>
     * 코스가 없으면 아무것도 하지 않는다. 완주 후 코스를 지우고 일지를 쓰는 흐름이 가능한데,
     * 그때 일지 작성을 실패시키면 안 된다 — 삭제된 코스는 어차피 둘러보기 대상이 아니라
     * 연결할 필요도 없다.
     */
    public void linkJournal(Long courseId, Long journalId) {
        if (journalId == null) {
            throw new IllegalArgumentException("journalId는 필수입니다.");
        }

        courseRepository.findById(courseId).ifPresentOrElse(
                course -> {
                    // 코스 하나에 일지도 하나다(스탬프가 코스당 1개, 일지가 스탬프당 1개).
                    // 다른 일지가 걸려 있다면 그 전제가 깨진 것이라 남겨서 확인할 수 있게 한다.
                    if (course.getJournalId() != null && !course.getJournalId().equals(journalId)) {
                        log.warn("이미 다른 여행일지가 연결된 코스: courseId={}, 기존 journalId={}, 새 journalId={}",
                                courseId, course.getJournalId(), journalId);
                    }
                    course.linkJournal(journalId);
                },
                () -> log.warn("연결할 코스가 없어 여행일지 연결을 건너뜀: courseId={}, journalId={}", courseId, journalId)
        );
    }

    /**
     * 여행일지 삭제 시 코스와의 연결을 끊는다. 여행일지 도메인이 호출한다.
     * <p>
     * 끊지 않으면 삭제된 일지를 가리킨 채로 남아 둘러보기 조회가 조인에 실패하고,
     * 같은 스탬프로 일지를 다시 쓰면 코스가 옛 일지를 가리키게 된다.
     * <p>
     * 연결된 코스가 없으면 아무것도 하지 않는다(애초에 연결된 적 없거나 코스가 이미 삭제된 경우).
     */
    public void unlinkJournal(Long journalId) {
        if (journalId == null) {
            throw new IllegalArgumentException("journalId는 필수입니다.");
        }

        courseRepository.findByJournalId(journalId).ifPresentOrElse(
                Course::unlinkJournal,
                () -> log.warn("연결된 코스가 없어 여행일지 해제를 건너뜀: journalId={}", journalId)
        );
    }

    public CourseCreateResponse createCourse(Long memberId, CourseCreateRequest request) {
        validateUserText(request.name());
        validateDistinctPlaces(request.placeIds());
        validateDrawableStation(request.stationId());

        Course savedCourse = courseRepository.save(courseConverter.toCourse(memberId, request));
        coursePlaceRepository.saveAll(courseConverter.toCoursePlaces(savedCourse.getId(), request.placeIds()));
        return courseConverter.toCreateResponse(savedCourse);
    }

    /**
     * "내 코스로 만들기"
     * 타인의 공개 코스를 편집 가능한 내 코스로 복제한다.
     * 원본을 참조만 하는 좋아요와 달리, 복제본은 원본이 삭제되거나 비공개로 바뀌어도 그대로 남는다.
     * 저장 화면에서 이름 입력과 순서 변경이 함께 일어나므로 한 번의 요청으로 처리한다.
     */
    public CourseCreateResponse copyCourse(Long memberId, Long courseId, CourseCopyRequest request) {
        validateUserText(request.name());
        Course original = findCopyableCourse(memberId, courseId);
        List<Long> placeIds = resolveCopiedPlaceOrder(courseId, request.placeIds());

        Course copied = courseRepository.save(courseConverter.toCopiedCourse(memberId, original, request.name()));
        coursePlaceRepository.saveAll(courseConverter.toCoursePlaces(copied.getId(), placeIds));
        return courseConverter.toCreateResponse(copied);
    }

    /**
     * 코스 이름·장소 순서를 수정한다. 둘 다 선택 사항이며 요청에 있는 필드만 반영한다
     * (둘 다 없으면 컨트롤러의 Bean Validation에서 이미 400으로 막힌다).
     * 한 트랜잭션으로 처리되므로 장소 순서 검증에 실패하면 이름 변경도 함께 롤백된다.
     */
    public CourseUpdateResponse updateCourse(Long memberId, Long courseId, CourseUpdateRequest request) {
        validateUserText(request.name());
        if (request.placeIds() != null) {
            validateDistinctPlaces(request.placeIds());
        }

        Course course = findOwnedCourse(memberId, courseId);

        if (request.placeIds() != null) {
            reorder(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(courseId), request.placeIds());
        }
        if (request.name() != null) {
            course.updateName(request.name());
        }

        return courseConverter.toUpdateResponse(course);
    }

    // 같은 장소를 한 코스에 두 번 담을 수 없다.
    private void validateDistinctPlaces(List<Long> placeIds) {
        if (new HashSet<>(placeIds).size() != placeIds.size()) {
            throw new CustomException(CourseErrorCode.DUPLICATE_COURSE_PLACES);
        }
    }

    private void validateUserText(String text) {
        if (profanityFilter.containsBannedWord(text)) {
            throw new CustomException(GlobalErrorCode.CONTAINS_BANNED_WORD);
        }
    }

    // 코스는 뽑기 대상 역(is_drawable=true)에만 붙는다.
    private void validateDrawableStation(Long stationId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.STATION_NOT_FOUND));
        if (!station.isDrawable()) {
            throw new CustomException(CourseErrorCode.STATION_NOT_DRAWABLE);
        }
    }

    // 요청한 placeIds가 코스의 장소 구성과 정확히 일치하는지 검증한다 (개수·구성 모두).
    // 순서만 바꿀 수 있고 장소를 빼거나 더할 수는 없어서, 순서 수정과 복제가 같은 규칙을 공유한다.
    private void validateSamePlaceComposition(Collection<Long> coursePlaceIds, List<Long> requestedPlaceIds) {
        if (coursePlaceIds.size() != requestedPlaceIds.size()
                || !new HashSet<>(coursePlaceIds).equals(new HashSet<>(requestedPlaceIds))) {
            throw new CustomException(CourseErrorCode.INVALID_COURSE_PLACES);
        }
    }

    // 복제 대상은 "타인의 공개 코스"로 제한한다. 좋아요와 같은 조건이다.
    private Course findCopyableCourse(Long memberId, Long courseId) {
        // 삭제된 코스는 Course의 @SQLRestriction 으로 조회에서 자동 제외된다.
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_NOT_FOUND));

        if (course.getMemberId().equals(memberId)) {
            throw new CustomException(CourseErrorCode.CANNOT_COPY_OWN_COURSE);
        }

        // 남의 비공개 코스가 존재한다는 사실이 드러나지 않도록 403이 아닌 404로 응답한다.
        if (!courseRepository.existsPublicById(courseId)) {
            log.warn("공개되지 않은 코스 복제 시도: memberId={}, courseId={}", memberId, courseId);
            throw new CustomException(CourseErrorCode.COURSE_NOT_FOUND);
        }
        return course;
    }

    /**
     * 복제본에 적용할 장소 순서를 정한다.
     * 화면에서 순서 변경만 가능하고 장소를 빼거나 더할 수는 없으므로,
     * 요청한 목록은 원본의 장소 구성과 정확히 일치해야 한다. 생략하면 원본 순서를 그대로 쓴다.
     */
    private List<Long> resolveCopiedPlaceOrder(Long courseId, List<Long> requestedPlaceIds) {
        List<Long> originalPlaceIds = coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(courseId).stream()
                .map(CoursePlace::getPlaceId)
                .toList();

        if (requestedPlaceIds == null || requestedPlaceIds.isEmpty()) {
            return originalPlaceIds;
        }

        validateDistinctPlaces(requestedPlaceIds);
        validateSamePlaceComposition(originalPlaceIds, requestedPlaceIds);
        return requestedPlaceIds;
    }

    private Course findOwnedCourse(Long memberId, Long courseId) {
        // 삭제된 코스는 Course의 @SQLRestriction 으로 조회에서 자동 제외된다.
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_NOT_FOUND));
        if (!course.getMemberId().equals(memberId)) {
            throw new CustomException(CourseErrorCode.COURSE_FORBIDDEN);
        }
        return course;
    }

    private void reorder(List<CoursePlace> coursePlaces, List<Long> orderedPlaceIds) {
        Map<Long, CoursePlace> byPlaceId = coursePlaces.stream()
                .collect(Collectors.toMap(CoursePlace::getPlaceId, Function.identity()));
        validateSamePlaceComposition(byPlaceId.keySet(), orderedPlaceIds);
        for (int index = 0; index < orderedPlaceIds.size(); index++) {
            byPlaceId.get(orderedPlaceIds.get(index)).updateOrderNum(index + 1);
        }
    }
}
