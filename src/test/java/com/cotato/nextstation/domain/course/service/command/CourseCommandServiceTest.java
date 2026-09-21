package com.cotato.nextstation.domain.course.service.command;

import com.cotato.nextstation.domain.course.converter.CourseConverter;
import com.cotato.nextstation.domain.course.dto.request.CourseCopyRequest;
import com.cotato.nextstation.domain.course.dto.request.CourseCreateRequest;
import com.cotato.nextstation.domain.course.dto.request.CourseUpdateRequest;
import com.cotato.nextstation.domain.course.entity.Course;
import com.cotato.nextstation.domain.course.entity.CoursePlace;
import com.cotato.nextstation.domain.course.exception.CourseErrorCode;
import com.cotato.nextstation.domain.course.repository.CoursePlaceRepository;
import com.cotato.nextstation.domain.course.repository.CourseRepository;
import com.cotato.nextstation.domain.station.entity.Station;
import com.cotato.nextstation.domain.station.repository.StationRepository;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.util.ProfanityFilter;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseCommandServiceTest {

    @InjectMocks
    private CourseCommandService courseCommandService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CoursePlaceRepository coursePlaceRepository;

    @Mock
    private CourseConverter courseConverter;

    @Mock
    private CourseViewCountUpdater courseViewCountUpdater;

    @Mock
    private StationRepository stationRepository;

    @Mock
    private ProfanityFilter profanityFilter;

    private Course course(String name) {
        return Course.builder().memberId(1L).stationId(100L).name(name).build();
    }

    private Station station(boolean isDrawable) {
        return Station.builder().stationName("성수역").isDrawable(isDrawable).build();
    }

    private CoursePlace coursePlace(Long placeId, int orderNum) {
        return CoursePlace.builder().courseId(1L).placeId(placeId).orderNum(orderNum).build();
    }

    @Test
    @DisplayName("코스를 생성하면 코스와 장소들이 저장된다")
    void createCourse_success() {
        // given
        CourseCreateRequest request = new CourseCreateRequest("성수 코스", 100L, List.of(10L, 20L, 30L));
        Course saved = course("성수 코스");
        List<CoursePlace> places = List.of(coursePlace(10L, 1), coursePlace(20L, 2), coursePlace(30L, 3));
        given(stationRepository.findById(100L)).willReturn(Optional.of(station(true)));
        given(courseConverter.toCourse(1L, request)).willReturn(saved);
        given(courseRepository.save(saved)).willReturn(saved);
        given(courseConverter.toCoursePlaces(saved.getId(), request.placeIds())).willReturn(places);

        // when
        courseCommandService.createCourse(1L, request);

        // then
        verify(courseRepository).save(saved);
        verify(coursePlaceRepository).saveAll(places);
        verify(courseConverter).toCreateResponse(saved);
    }

    @Test
    @DisplayName("같은 장소가 중복된 요청으로 코스를 생성하면 예외가 발생하고 저장하지 않는다")
    void createCourse_duplicatePlaces() {
        // given
        CourseCreateRequest request = new CourseCreateRequest("성수 코스", 100L, List.of(10L, 10L, 20L));

        // when & then
        assertThatThrownBy(() -> courseCommandService.createCourse(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.DUPLICATE_COURSE_PLACES.getMessage());
        verify(courseRepository, never()).save(any());
        verify(coursePlaceRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("존재하지 않는 역으로 코스를 생성하면 예외가 발생하고 저장하지 않는다")
    void createCourse_stationNotFound() {
        // given
        CourseCreateRequest request = new CourseCreateRequest("성수 코스", 999L, List.of(10L, 20L, 30L));
        given(stationRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> courseCommandService.createCourse(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.STATION_NOT_FOUND.getMessage());
        verify(courseRepository, never()).save(any());
        verify(coursePlaceRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("뽑기 대상이 아닌 역으로 코스를 생성하면 예외가 발생하고 저장하지 않는다")
    void createCourse_stationNotDrawable() {
        // given
        CourseCreateRequest request = new CourseCreateRequest("성수 코스", 100L, List.of(10L, 20L, 30L));
        given(stationRepository.findById(100L)).willReturn(Optional.of(station(false)));

        // when & then
        assertThatThrownBy(() -> courseCommandService.createCourse(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.STATION_NOT_DRAWABLE.getMessage());
        verify(courseRepository, never()).save(any());
        verify(coursePlaceRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("타인의 공개 코스를 복제하면 원본을 가리키는 새 코스와 장소들이 저장된다")
    void copyCourse_success() {
        // given — 원본은 1번 회원 소유, 2번 회원이 복제한다
        Course original = course("원본 코스");
        Course copied = course("내 코스");
        List<CoursePlace> originalPlaces = List.of(coursePlace(10L, 1), coursePlace(20L, 2), coursePlace(30L, 3));
        List<CoursePlace> copiedPlaces = List.of(coursePlace(10L, 1), coursePlace(20L, 2), coursePlace(30L, 3));
        given(courseRepository.findById(1L)).willReturn(Optional.of(original));
        given(courseRepository.existsPublicById(1L)).willReturn(true);
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(1L)).willReturn(originalPlaces);
        given(courseConverter.toCopiedCourse(2L, original, "내 코스")).willReturn(copied);
        given(courseRepository.save(copied)).willReturn(copied);
        given(courseConverter.toCoursePlaces(copied.getId(), List.of(10L, 20L, 30L))).willReturn(copiedPlaces);

        // when
        courseCommandService.copyCourse(2L, 1L, new CourseCopyRequest("내 코스", null));

        // then
        verify(courseRepository).save(copied);
        verify(coursePlaceRepository).saveAll(copiedPlaces);
        verify(courseConverter).toCreateResponse(copied);
    }

    @Test
    @DisplayName("placeIds를 넘기면 그 순서대로 복제본의 장소 순서가 부여된다")
    void copyCourse_withReorderedPlaces() {
        // given
        Course original = course("원본 코스");
        Course copied = course("내 코스");
        List<CoursePlace> originalPlaces = List.of(coursePlace(10L, 1), coursePlace(20L, 2), coursePlace(30L, 3));
        given(courseRepository.findById(1L)).willReturn(Optional.of(original));
        given(courseRepository.existsPublicById(1L)).willReturn(true);
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(1L)).willReturn(originalPlaces);
        given(courseConverter.toCopiedCourse(2L, original, "내 코스")).willReturn(copied);
        given(courseRepository.save(copied)).willReturn(copied);

        // when
        courseCommandService.copyCourse(2L, 1L, new CourseCopyRequest("내 코스", List.of(30L, 10L, 20L)));

        // then — 원본 순서가 아니라 요청한 순서로 장소가 만들어진다
        verify(courseConverter).toCoursePlaces(copied.getId(), List.of(30L, 10L, 20L));
    }

    @Test
    @DisplayName("본인이 만든 코스를 복제하면 예외가 발생하고 저장하지 않는다")
    void copyCourse_ownCourse() {
        // given — 코스 소유자와 요청자가 모두 1번 회원
        given(courseRepository.findById(1L)).willReturn(Optional.of(course("내가 만든 코스")));

        // when & then
        assertThatThrownBy(() -> courseCommandService.copyCourse(1L, 1L, new CourseCopyRequest("복사본", null)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.CANNOT_COPY_OWN_COURSE.getMessage());
        verify(courseRepository, never()).save(any());
        verify(coursePlaceRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("공개되지 않은 코스를 복제하면 존재 여부를 숨기기 위해 404 예외가 발생한다")
    void copyCourse_notPublic() {
        // given
        given(courseRepository.findById(1L)).willReturn(Optional.of(course("비공개 코스")));
        given(courseRepository.existsPublicById(1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> courseCommandService.copyCourse(2L, 1L, new CourseCopyRequest("복사본", null)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_NOT_FOUND.getMessage());
        verify(courseRepository, never()).save(any());
    }

    @Test
    @DisplayName("원본 구성과 다른 placeIds로 복제하면 예외가 발생한다")
    void copyCourse_placeMismatch() {
        // given
        given(courseRepository.findById(1L)).willReturn(Optional.of(course("원본 코스")));
        given(courseRepository.existsPublicById(1L)).willReturn(true);
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(1L))
                .willReturn(List.of(coursePlace(10L, 1), coursePlace(20L, 2), coursePlace(30L, 3)));

        // when & then — 원본에 없는 99번 장소를 끼워 넣었다
        assertThatThrownBy(() -> courseCommandService.copyCourse(
                2L, 1L, new CourseCopyRequest("복사본", List.of(10L, 20L, 99L))))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.INVALID_COURSE_PLACES.getMessage());
        verify(courseRepository, never()).save(any());
    }

    @Test
    @DisplayName("이름과 장소 순서를 함께 요청하면 둘 다 반영된다")
    void updateCourse_bothFields_success() {
        // given
        Course course = course("이전 이름");
        CoursePlace place10 = coursePlace(10L, 1);
        CoursePlace place20 = coursePlace(20L, 2);
        CoursePlace place30 = coursePlace(30L, 3);
        given(courseRepository.findById(1L)).willReturn(Optional.of(course));
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(1L))
                .willReturn(List.of(place10, place20, place30));

        // when
        courseCommandService.updateCourse(1L, 1L, new CourseUpdateRequest("새 이름", List.of(30L, 10L, 20L)));

        // then
        assertThat(course.getName()).isEqualTo("새 이름");
        assertThat(place30.getOrderNum()).isEqualTo(1);
        assertThat(place10.getOrderNum()).isEqualTo(2);
        assertThat(place20.getOrderNum()).isEqualTo(3);
        verify(courseConverter).toUpdateResponse(course);
    }

    @Test
    @DisplayName("이름만 요청하면 이름만 바뀌고 장소 순서는 조회하지 않는다")
    void updateCourse_nameOnly() {
        // given
        Course course = course("이전 이름");
        given(courseRepository.findById(1L)).willReturn(Optional.of(course));

        // when
        courseCommandService.updateCourse(1L, 1L, new CourseUpdateRequest("새 이름", null));

        // then
        assertThat(course.getName()).isEqualTo("새 이름");
        verify(coursePlaceRepository, never()).findByCourseIdOrderByOrderNumAsc(any());
    }

    @Test
    @DisplayName("장소 순서만 요청하면 순서만 바뀌고 이름은 그대로다")
    void updateCourse_placeIdsOnly() {
        // given
        Course course = course("이전 이름");
        CoursePlace place10 = coursePlace(10L, 1);
        CoursePlace place20 = coursePlace(20L, 2);
        CoursePlace place30 = coursePlace(30L, 3);
        given(courseRepository.findById(1L)).willReturn(Optional.of(course));
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(1L))
                .willReturn(List.of(place10, place20, place30));

        // when
        courseCommandService.updateCourse(1L, 1L, new CourseUpdateRequest(null, List.of(30L, 10L, 20L)));

        // then
        assertThat(course.getName()).isEqualTo("이전 이름");
        assertThat(place30.getOrderNum()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 코스를 수정하면 예외가 발생한다")
    void updateCourse_notFound() {
        // given
        given(courseRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> courseCommandService.updateCourse(1L, 1L, new CourseUpdateRequest("새 이름", null)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("타인 소유 코스를 수정하면 권한 예외가 발생하고 이름이 바뀌지 않는다")
    void updateCourse_forbidden() {
        // given — 코스 소유자는 1번 회원, 요청자는 2번 회원
        Course course = course("이전 이름");
        given(courseRepository.findById(1L)).willReturn(Optional.of(course));

        // when & then
        assertThatThrownBy(() -> courseCommandService.updateCourse(2L, 1L, new CourseUpdateRequest("새 이름", null)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.COURSE_FORBIDDEN.getMessage());
        assertThat(course.getName()).isEqualTo("이전 이름");
    }

    @Test
    @DisplayName("중복된 장소로 순서를 요청하면 중복 예외가 발생하고 코스는 조회되지 않는다")
    void updateCourse_duplicatePlaces() {
        // when & then
        assertThatThrownBy(() -> courseCommandService.updateCourse(
                1L, 1L, new CourseUpdateRequest("새 이름", List.of(10L, 10L, 20L))))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.DUPLICATE_COURSE_PLACES.getMessage());
        verify(courseRepository, never()).findById(any());
    }

    @Test
    @DisplayName("코스에 없는 장소로 순서를 요청하면 예외가 발생하고 이름도 바뀌지 않는다(롤백)")
    void updateCourse_placeMismatch_rollsBackName() {
        // given
        Course course = course("이전 이름");
        given(courseRepository.findById(1L)).willReturn(Optional.of(course));
        given(coursePlaceRepository.findByCourseIdOrderByOrderNumAsc(1L))
                .willReturn(List.of(coursePlace(10L, 1), coursePlace(20L, 2), coursePlace(30L, 3)));

        // when & then: 장소 순서 검증에 실패하면 이름 변경도 함께 막혀야 한다
        assertThatThrownBy(() -> courseCommandService.updateCourse(
                1L, 1L, new CourseUpdateRequest("새 이름", List.of(10L, 20L, 99L))))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(CourseErrorCode.INVALID_COURSE_PLACES.getMessage());
        assertThat(course.getName()).isEqualTo("이전 이름");
    }

    // ---------- 조회수 증가 ----------

    @Test
    @DisplayName("조회수 증가는 별도 트랜잭션을 여는 updater로 위임한다")
    void increaseViewCount_delegates() {
        // when
        courseCommandService.increaseViewCount(1L, 2L);

        // then: 코스 상세 조회가 읽기 전용 트랜잭션이라 같은 트랜잭션에서 UPDATE할 수 없다
        verify(courseViewCountUpdater).increaseViewCount(1L, 2L);
    }

    @Test
    @DisplayName("조회수 증가가 실패해도 예외를 밖으로 던지지 않는다")
    void increaseViewCount_swallowsFailure() {
        // given: 조회수는 부가 정보라 잠금 경합 등으로 실패해도 코스 상세 화면은 떠야 한다
        willThrow(new DataIntegrityViolationException("잠금 경합"))
                .given(courseViewCountUpdater).increaseViewCount(1L, 2L);

        // when & then
        assertThatCode(() -> courseCommandService.increaseViewCount(1L, 2L))
                .doesNotThrowAnyException();
    }

    // ---------- 여행일지 연결/해제 ----------

    @Test
    @DisplayName("여행일지를 작성하면 코스가 그 일지를 가리킨다")
    void linkJournal_success() {
        // given
        Course course = course("보문역 코스");
        given(courseRepository.findById(1L)).willReturn(Optional.of(course));

        // when
        courseCommandService.linkJournal(1L, 50L);

        // then: 이 값이 있어야 둘러보기·검색·좋아요·복제에서 공개 코스로 판정된다
        assertThat(course.getJournalId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("코스가 없으면 여행일지 연결을 건너뛰고 예외를 던지지 않는다")
    void linkJournal_skipsWhenCourseMissing() {
        // given: 완주 후 코스를 지우고 일지를 쓸 수 있는데, 그때 일지 작성이 실패하면 안 된다
        given(courseRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatCode(() -> courseCommandService.linkJournal(1L, 50L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("journalId 없이 연결을 시도하면 예외가 발생한다")
    void linkJournal_rejectsNullJournalId() {
        // when & then: 호출부의 실수라 조용히 넘기지 않는다
        assertThatThrownBy(() -> courseCommandService.linkJournal(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(courseRepository, never()).findById(any());
    }

    @Test
    @DisplayName("여행일지를 삭제하면 코스의 연결이 끊긴다")
    void unlinkJournal_success() {
        // given
        Course course = course("보문역 코스");
        course.linkJournal(50L);
        given(courseRepository.findByJournalId(50L)).willReturn(Optional.of(course));

        // when
        courseCommandService.unlinkJournal(50L);

        // then: 일지가 사라진 코스는 둘러보기에서 빠지고 "내가 만든 코스"에만 남는다
        assertThat(course.getJournalId()).isNull();
    }

    @Test
    @DisplayName("연결된 코스가 없으면 여행일지 해제는 아무것도 하지 않는다")
    void unlinkJournal_skipsWhenNoLinkedCourse() {
        // given
        given(courseRepository.findByJournalId(50L)).willReturn(Optional.empty());

        // when & then
        assertThatCode(() -> courseCommandService.unlinkJournal(50L))
                .doesNotThrowAnyException();
    }
}
