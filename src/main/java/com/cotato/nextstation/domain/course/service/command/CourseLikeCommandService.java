package com.cotato.nextstation.domain.course.service.command;

import com.cotato.nextstation.domain.course.entity.Course;
import com.cotato.nextstation.domain.course.entity.CourseLike;
import com.cotato.nextstation.domain.course.exception.CourseErrorCode;
import com.cotato.nextstation.domain.course.repository.CourseRepository;
import com.cotato.nextstation.domain.course.repository.CourseLikeRepository;
import com.cotato.nextstation.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CourseLikeCommandService {

    private final CourseRepository courseRepository;
    private final CourseLikeRepository courseLikeRepository;

    // 코스 좋아요. 본인 코스 포함, "공개 코스"만 대상으로 한다.
    public void likeCourse(Long memberId, Long courseId) {
        validateLikeable(memberId, courseId);

        if (courseLikeRepository.existsByMemberIdAndCourseId(memberId, courseId)) {
            throw new CustomException(CourseErrorCode.DUPLICATE_COURSE_LIKE);
        }

        try {
            courseLikeRepository.saveAndFlush(
                    CourseLike.builder().memberId(memberId).courseId(courseId).build()
            );
        } catch (DataIntegrityViolationException e) {
            // 위 존재 확인 이후 동시에 같은 요청이 먼저 좋아요된 경우 (레이스 컨디션)
            log.warn("코스 중복 좋아요 시도(레이스 컨디션): memberId={}, courseId={}", memberId, courseId);
            throw new CustomException(CourseErrorCode.DUPLICATE_COURSE_LIKE);
        }

        courseRepository.increaseLikeCount(courseId);
    }

    // 코스 좋아요 취소. 좋아요하지 않은 코스면 404.
    // 조회 없이 바로 삭제하고 지워진 행 수로 판단한다. 조회 후 삭제하면 동시 취소 시
    // 두 요청이 모두 삭제에 성공했다고 보고 좋아요 수를 각각 줄인다.
    public void cancelLike(Long memberId, Long courseId) {
        if (courseLikeRepository.deleteByMemberIdAndCourseId(memberId, courseId) == 0) {
            throw new CustomException(CourseErrorCode.COURSE_LIKE_NOT_FOUND);
        }

        courseRepository.decreaseLikeCount(courseId);
    }

    /**
     * 좋아요 다중 취소(좋아요 목록의 선택 모드). 요청한 코스 중 실제로 좋아요돼 있는 것만 취소한다.
     * 다른 기기에서 이미 취소된 코스가 섞여 있어도 나머지는 정상 처리되도록 부분 성공을 허용하고,
     * 하나도 좋아요돼 있지 않을 때만 예외로 알린다.
     */
    public void cancelLikes(Long memberId, List<Long> courseIds) {
        List<Long> targetCourseIds = courseIds.stream().distinct().toList();

        // 취소할 좋아요 행을 먼저 비관적 잠금으로 확보한다.
        // 같은 회원이 같은 코스를 동시에 취소해도, 한 요청이 잠금을 잡아 직렬화되므로
        // 뒤 요청은 앞 요청이 삭제·커밋한 뒤라 빈 결과가 되어 좋아요 수가 중복 감소하지 않는다.
        List<Long> lockedCourseIds = courseLikeRepository.findLikedCourseIdsForUpdate(memberId, targetCourseIds);
        if (lockedCourseIds.isEmpty()) {
            throw new CustomException(CourseErrorCode.COURSE_LIKE_NOT_FOUND);
        }

        // 확보한 행만 정확히 감소·삭제한다. 잠금으로 "지운 것만 감소" 원칙이 보장된다.
        courseRepository.decreaseLikeCountAll(memberId, lockedCourseIds);
        courseLikeRepository.deleteByMemberIdAndCourseIdIn(memberId, lockedCourseIds);
    }

    /**
     * 좋아요 전체 취소(좋아요 목록의 "모두 선택").
     * 갤러리의 전체 선택처럼 아직 화면에 불러오지 않은 좋아요까지 대상에 넣어야 하므로,
     * 프론트가 가진 id 목록이 아니라 서버가 대상을 다시 조회한다.
     * 전체 선택 후 일부만 해제한 경우는 exceptCourseIds로 받는다(해제는 보이는 항목에서만 가능).
     */
    public void cancelAllLikes(Long memberId, List<Long> exceptCourseIds) {
        Set<Long> excluded = exceptCourseIds == null ? Set.of() : Set.copyOf(exceptCourseIds);

        List<Long> targetCourseIds = courseLikeRepository.findVisibleLikedCourseIds(memberId, memberId).stream()
                .filter(courseId -> !excluded.contains(courseId))
                .toList();

        if (targetCourseIds.isEmpty()) {
            throw new CustomException(CourseErrorCode.COURSE_LIKE_NOT_FOUND);
        }

        cancelLikes(memberId, targetCourseIds);
    }

    /**
     * 코스 다중 삭제(저장 탭 선택 모드). 본인 코스만 대상이 되며, 남의 코스나 이미 삭제된 코스가
     * 섞여 있어도 나머지는 정상 삭제되는 부분 성공을 허용한다. soft delete라 좋아요 다중 취소와
     * 달리 카운터를 다루지 않으므로 동시 삭제에 대한 잠금이 필요 없다(재삭제는 @SQLRestriction으로
     * 자동 제외되어 그저 대상에서 빠질 뿐이다).
     */
    public void deleteCourses(Long memberId, List<Long> courseIds) {
        List<Long> targetCourseIds = courseIds.stream().distinct().toList();

        List<Course> courses = courseRepository.findAllByMemberIdAndIdIn(memberId, targetCourseIds);
        if (courses.isEmpty()) {
            throw new CustomException(CourseErrorCode.COURSE_NOT_FOUND);
        }

        courses.forEach(Course::delete);
    }

    private void validateLikeable(Long memberId, Long courseId) {
        // 삭제된 코스는 Course의 @SQLRestriction으로 조회에서 자동 제외된다.
        courseRepository.findById(courseId)
                .orElseThrow(() -> new CustomException(CourseErrorCode.COURSE_NOT_FOUND));

        // 공개되지 않은 코스는 좋아요해도 목록에 뜨지 않으므로 애초에 막는다. 본인 코스도 예외 없이 적용된다.
        // 남의 비공개 코스가 존재한다는 사실이 드러나지 않도록 404로 응답한다.
        if (!courseRepository.existsPublicById(courseId)) {
            log.warn("공개되지 않은 코스 좋아요 시도: memberId={}, courseId={}", memberId, courseId);
            throw new CustomException(CourseErrorCode.COURSE_NOT_FOUND);
        }
    }
}
