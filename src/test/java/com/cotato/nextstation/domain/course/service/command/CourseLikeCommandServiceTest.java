package com.cotato.nextstation.domain.course.service.command;

import com.cotato.nextstation.domain.course.entity.Course;
import com.cotato.nextstation.domain.course.entity.CourseLike;
import com.cotato.nextstation.domain.course.exception.CourseErrorCode;
import com.cotato.nextstation.domain.course.repository.CourseRepository;
import com.cotato.nextstation.domain.course.repository.CourseLikeRepository;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseLikeCommandServiceTest {

    @InjectMocks
    private CourseLikeCommandService courseLikeCommandService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseLikeRepository courseLikeRepository;

    private Course course(Long id, Long memberId) {
        Course course = Course.builder().memberId(memberId).stationId(10L).name("보문역 코스").build();
        ReflectionTestUtils.setField(course, "id", id);
        return course;
    }

    @Test
    @DisplayName("코스를 좋아요하면 회원/코스가 정확히 저장되고 좋아요 수가 증가한다")
    void likeCourse_success() {
        // given: memberId와 courseId를 다른 값으로 둬야 둘이 뒤바뀌는 실수를 잡을 수 있다
        Long memberId = 1L;
        Long courseId = 7L;
        given(courseRepository.findById(courseId)).willReturn(Optional.of(course(courseId, 2L)));
        given(courseRepository.existsPublicById(courseId)).willReturn(true);
        given(courseLikeRepository.existsByMemberIdAndCourseId(memberId, courseId)).willReturn(false);

        // when
        courseLikeCommandService.likeCourse(memberId, courseId);

        // then
        ArgumentCaptor<CourseLike> captor = ArgumentCaptor.forClass(CourseLike.class);
        verify(courseLikeRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getMemberId()).isEqualTo(memberId);
        assertThat(captor.getValue().getCourseId()).isEqualTo(courseId);
        verify(courseRepository).increaseLikeCount(courseId);
    }

    @Test
    @DisplayName("이미 좋아요한 코스를 다시 좋아요하면 예외가 발생하고 좋아요 수가 늘지 않는다")
    void likeCourse_duplicate() {
        // given
        given(courseRepository.findById(1L)).willReturn(Optional.of(course(1L, 2L)));
        given(courseRepository.existsPublicById(1L)).willReturn(true);
        given(courseLikeRepository.existsByMemberIdAndCourseId(1L, 1L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> courseLikeCommandService.likeCourse(1L, 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.DUPLICATE_COURSE_LIKE.getMessage());
        verify(courseLikeRepository, never()).saveAndFlush(any());
        verify(courseRepository, never()).increaseLikeCount(any());
    }

    @Test
    @DisplayName("동시 요청으로 유니크 제약에 걸리면 중복 저장 예외로 응답한다")
    void likeCourse_raceCondition() {
        // given: 중복 확인은 통과했지만 저장 시점에 다른 요청이 먼저 커밋된 상황
        given(courseRepository.findById(1L)).willReturn(Optional.of(course(1L, 2L)));
        given(courseRepository.existsPublicById(1L)).willReturn(true);
        given(courseLikeRepository.existsByMemberIdAndCourseId(1L, 1L)).willReturn(false);
        willThrow(new DataIntegrityViolationException("unique"))
                .given(courseLikeRepository).saveAndFlush(any(CourseLike.class));

        // when & then
        assertThatThrownBy(() -> courseLikeCommandService.likeCourse(1L, 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.DUPLICATE_COURSE_LIKE.getMessage());
        verify(courseRepository, never()).increaseLikeCount(any());
    }

    @Test
    @DisplayName("존재하지 않는 코스는 좋아요할 수 없다")
    void likeCourse_courseNotFound() {
        // given
        given(courseRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> courseLikeCommandService.likeCourse(1L, 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_NOT_FOUND.getMessage());
        verify(courseLikeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("본인이 만든 코스도 좋아요할 수 있다")
    void likeCourse_ownCourse() {
        // given: 1번 회원이 자기 코스(memberId=1)를 좋아요 시도
        given(courseRepository.findById(1L)).willReturn(Optional.of(course(1L, 1L)));
        given(courseRepository.existsPublicById(1L)).willReturn(true);
        given(courseLikeRepository.existsByMemberIdAndCourseId(1L, 1L)).willReturn(false);

        // when
        courseLikeCommandService.likeCourse(1L, 1L);

        // then
        verify(courseLikeRepository).saveAndFlush(any(CourseLike.class));
        verify(courseRepository).increaseLikeCount(1L);
    }

    @Test
    @DisplayName("공개되지 않은 코스는 타인 것이어도 좋아요할 수 없다")
    void likeCourse_notPublic() {
        // given: 타인 코스지만 일지가 없거나 비공개인 경우
        given(courseRepository.findById(1L)).willReturn(Optional.of(course(1L, 2L)));
        given(courseRepository.existsPublicById(1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> courseLikeCommandService.likeCourse(1L, 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_NOT_FOUND.getMessage());
        verify(courseLikeRepository, never()).saveAndFlush(any());
        verify(courseRepository, never()).increaseLikeCount(any());
    }

    @Test
    @DisplayName("공개되지 않은 코스는 본인 것이어도 좋아요할 수 없다")
    void likeCourse_notPublic_ownCourse() {
        // given: 본인 코스지만 일지가 없거나 비공개인 경우
        given(courseRepository.findById(1L)).willReturn(Optional.of(course(1L, 1L)));
        given(courseRepository.existsPublicById(1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> courseLikeCommandService.likeCourse(1L, 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_NOT_FOUND.getMessage());
        verify(courseLikeRepository, never()).saveAndFlush(any());
        verify(courseRepository, never()).increaseLikeCount(any());
    }

    @Test
    @DisplayName("좋아요를 취소하면 삭제되고 좋아요 수가 감소한다")
    void cancelLike_success() {
        // given: 삭제 쿼리가 1행을 지웠다 = 실제로 좋아요돼 있었다
        given(courseLikeRepository.deleteByMemberIdAndCourseId(1L, 1L)).willReturn(1);

        // when
        courseLikeCommandService.cancelLike(1L, 1L);

        // then
        verify(courseRepository).decreaseLikeCount(1L);
    }

    @Test
    @DisplayName("좋아요하지 않은(또는 동시 취소로 이미 지워진) 코스를 취소하면 예외가 발생하고 좋아요 수가 줄지 않는다")
    void cancelLike_notLiked() {
        // given: 지워진 행이 없다 = 좋아요돼 있지 않았거나, 동시 취소로 다른 요청이 먼저 지웠다.
        //        어느 쪽이든 삭제 영향 행 수가 0이라 좋아요 수를 중복으로 깎지 않는다.
        given(courseLikeRepository.deleteByMemberIdAndCourseId(1L, 1L)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> courseLikeCommandService.cancelLike(1L, 1L))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_LIKE_NOT_FOUND.getMessage());
        verify(courseRepository, never()).decreaseLikeCount(any());
    }

    @Test
    @DisplayName("여러 좋아요를 취소하면 잠금으로 확보한 코스만 감소·삭제한다")
    void cancelLikes_success() {
        // given: 1,2,3을 요청했지만 2는 이미 취소돼 있어 잠금으로 확보되는 건 1,3뿐이다
        given(courseLikeRepository.findLikedCourseIdsForUpdate(1L, List.of(1L, 2L, 3L)))
                .willReturn(List.of(1L, 3L));

        // when
        courseLikeCommandService.cancelLikes(1L, List.of(1L, 2L, 3L));

        // then: 확보된 1,3만 정확히 감소·삭제된다 (이미 취소된 2는 대상에서 빠진다)
        verify(courseRepository).decreaseLikeCountAll(1L, List.of(1L, 3L));
        verify(courseLikeRepository).deleteByMemberIdAndCourseIdIn(1L, List.of(1L, 3L));
    }

    @Test
    @DisplayName("취소 대상을 잠금으로 확보한 뒤 감소·삭제한다")
    void cancelLikes_lockBeforeMutation() {
        // given: 동시 취소로 좋아요 수가 중복 감소하지 않도록, 확보(잠금) → 감소 → 삭제 순으로 진행한다
        given(courseLikeRepository.findLikedCourseIdsForUpdate(1L, List.of(1L))).willReturn(List.of(1L));

        // when
        courseLikeCommandService.cancelLikes(1L, List.of(1L));

        // then
        InOrder inOrder = inOrder(courseLikeRepository, courseRepository);
        inOrder.verify(courseLikeRepository).findLikedCourseIdsForUpdate(1L, List.of(1L));
        inOrder.verify(courseRepository).decreaseLikeCountAll(1L, List.of(1L));
        inOrder.verify(courseLikeRepository).deleteByMemberIdAndCourseIdIn(1L, List.of(1L));
    }

    @Test
    @DisplayName("확보된 좋아요가 하나도 없으면 예외가 발생하고 감소·삭제하지 않는다")
    void cancelLikes_noneLocked() {
        // given: 요청한 코스가 모두 이미 취소된 상태 (동시 취소 등)
        given(courseLikeRepository.findLikedCourseIdsForUpdate(1L, List.of(1L, 2L))).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> courseLikeCommandService.cancelLikes(1L, List.of(1L, 2L)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_LIKE_NOT_FOUND.getMessage());
        verify(courseRepository, never()).decreaseLikeCountAll(any(), any());
        verify(courseLikeRepository, never()).deleteByMemberIdAndCourseIdIn(any(), any());
    }

    @Test
    @DisplayName("같은 코스를 중복으로 보내도 한 번만 처리한다")
    void cancelLikes_duplicateIds() {
        // given: 중복 제거된 목록으로 잠금 조회가 호출된다
        given(courseLikeRepository.findLikedCourseIdsForUpdate(1L, List.of(1L))).willReturn(List.of(1L));

        // when
        courseLikeCommandService.cancelLikes(1L, List.of(1L, 1L, 1L));

        // then
        verify(courseLikeRepository).findLikedCourseIdsForUpdate(1L, List.of(1L));
        verify(courseLikeRepository).deleteByMemberIdAndCourseIdIn(1L, List.of(1L));
    }

    @Test
    @DisplayName("전체 취소는 화면에 안 불러온 좋아요까지 서버가 조회해 취소한다")
    void cancelAllLikes_success() {
        // given: 프론트가 첫 페이지 2개만 들고 있어도 서버는 5개 전부를 대상으로 삼는다
        given(courseLikeRepository.findVisibleLikedCourseIds(1L, 1L)).willReturn(List.of(1L, 2L, 3L, 4L, 5L));
        given(courseLikeRepository.findLikedCourseIdsForUpdate(1L, List.of(1L, 2L, 3L, 4L, 5L)))
                .willReturn(List.of(1L, 2L, 3L, 4L, 5L));

        // when
        courseLikeCommandService.cancelAllLikes(1L, null);

        // then
        verify(courseLikeRepository).deleteByMemberIdAndCourseIdIn(1L, List.of(1L, 2L, 3L, 4L, 5L));
    }

    @Test
    @DisplayName("전체 선택 후 해제한 코스는 취소 대상에서 빠진다")
    void cancelAllLikes_withExceptions() {
        // given: 전체 선택 뒤 2,4번을 해제한 경우
        given(courseLikeRepository.findVisibleLikedCourseIds(1L, 1L)).willReturn(List.of(1L, 2L, 3L, 4L, 5L));
        given(courseLikeRepository.findLikedCourseIdsForUpdate(1L, List.of(1L, 3L, 5L)))
                .willReturn(List.of(1L, 3L, 5L));

        // when
        courseLikeCommandService.cancelAllLikes(1L, List.of(2L, 4L));

        // then
        verify(courseRepository).decreaseLikeCountAll(1L, List.of(1L, 3L, 5L));
        verify(courseLikeRepository).deleteByMemberIdAndCourseIdIn(1L, List.of(1L, 3L, 5L));
    }

    @Test
    @DisplayName("취소할 좋아요가 없으면 예외가 발생한다")
    void cancelAllLikes_nothingToCancel() {
        // given
        given(courseLikeRepository.findVisibleLikedCourseIds(1L, 1L)).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> courseLikeCommandService.cancelAllLikes(1L, null))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_LIKE_NOT_FOUND.getMessage());
        verify(courseLikeRepository, never()).deleteByMemberIdAndCourseIdIn(any(), any());
    }

    @Test
    @DisplayName("전체 선택 후 모두 해제하면 예외가 발생한다")
    void cancelAllLikes_allExcluded() {
        // given
        given(courseLikeRepository.findVisibleLikedCourseIds(1L, 1L)).willReturn(List.of(1L, 2L));

        // when & then
        assertThatThrownBy(() -> courseLikeCommandService.cancelAllLikes(1L, List.of(1L, 2L)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_LIKE_NOT_FOUND.getMessage());
        verify(courseLikeRepository, never()).deleteByMemberIdAndCourseIdIn(any(), any());
    }

    @Test
    @DisplayName("선택한 코스를 다중 삭제하면 모두 soft delete 된다")
    void deleteCourses_success() {
        // given
        Course course1 = course(1L, 1L);
        Course course2 = course(2L, 1L);
        given(courseRepository.findAllByMemberIdAndIdIn(1L, List.of(1L, 2L)))
                .willReturn(List.of(course1, course2));

        // when
        courseLikeCommandService.deleteCourses(1L, List.of(1L, 2L));

        // then
        assertThat(course1.isDeleted()).isTrue();
        assertThat(course2.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("다중 삭제 요청에 중복 id가 섞여 있으면 중복을 제거하고 조회한다")
    void deleteCourses_distinctsDuplicateIds() {
        // given
        Course course1 = course(1L, 1L);
        given(courseRepository.findAllByMemberIdAndIdIn(1L, List.of(1L)))
                .willReturn(List.of(course1));

        // when
        courseLikeCommandService.deleteCourses(1L, List.of(1L, 1L));

        // then
        assertThat(course1.isDeleted()).isTrue();
        verify(courseRepository).findAllByMemberIdAndIdIn(1L, List.of(1L));
    }

    @Test
    @DisplayName("남의 코스나 존재하지 않는 코스가 섞여 있어도 본인 코스는 정상 삭제된다(부분 성공)")
    void deleteCourses_partialSuccess() {
        // given: 3개를 요청했는데 본인 소유는 1개뿐이라 그것만 조회된다
        Course course1 = course(1L, 1L);
        given(courseRepository.findAllByMemberIdAndIdIn(1L, List.of(1L, 2L, 3L)))
                .willReturn(List.of(course1));

        // when
        courseLikeCommandService.deleteCourses(1L, List.of(1L, 2L, 3L));

        // then
        assertThat(course1.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제 대상이 하나도 없으면 예외가 발생한다")
    void deleteCourses_notFound() {
        // given: 남의 코스거나 이미 삭제된 코스만 선택함
        given(courseRepository.findAllByMemberIdAndIdIn(1L, List.of(1L, 2L))).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> courseLikeCommandService.deleteCourses(1L, List.of(1L, 2L)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_NOT_FOUND.getMessage());
    }
}
