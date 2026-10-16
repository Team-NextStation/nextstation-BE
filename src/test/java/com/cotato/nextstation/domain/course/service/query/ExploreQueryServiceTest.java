package com.cotato.nextstation.domain.course.service.query;

import com.cotato.nextstation.domain.course.dto.response.ConceptTourResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreCourseListResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreLineResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreResponse;
import com.cotato.nextstation.domain.station.entity.LineCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExploreQueryServiceTest {

    @InjectMocks
    private ExploreQueryService exploreQueryService;

    @Mock
    private CourseQueryService courseQueryService;

    @Mock
    private ConceptTourQueryService conceptTourQueryService;

    private ExploreCourseListResponse emptyList() {
        return new ExploreCourseListResponse(List.of(), List.of(), null, false);
    }

    private ExploreLineResponse line(long id, String name, LineCode code, boolean hasCourses) {
        return new ExploreLineResponse(id, name, code, hasCourses);
    }

    private ConceptTourResponse conceptTour(long id) {
        return new ConceptTourResponse(id, "컨셉" + id, "설명", 0);
    }

    @Test
    @DisplayName("각 섹션을 화면에 보이는 개수만큼만 조회한다")
    void getExplore_sectionSizes() {
        // given
        given(courseQueryService.getMostLikedCourses(isNull(), isNull(), eq(6))).willReturn(emptyList());
        given(conceptTourQueryService.getConceptTours(isNull()))
                .willReturn(List.of(conceptTour(1), conceptTour(2), conceptTour(3), conceptTour(4)));
        given(courseQueryService.getExploreLines())
                .willReturn(List.of(line(4L, "1호선", LineCode.LINE_1, true)));
        given(courseQueryService.getLineCourses(isNull(), any(), eq(3))).willReturn(List.of());

        // when
        ExploreResponse result = exploreQueryService.getExplore(null);

        // then: 컨셉은 3개까지만 자른다
        assertThat(result.conceptTours()).hasSize(3);
        verify(courseQueryService).getMostLikedCourses(null, null, 6);
        verify(courseQueryService).getLineCourses(any(), any(), eq(3));
    }

    @Test
    @DisplayName("코스가 없는 노선도 목록에 남기고 코스가 있는 첫 노선을 선택한다")
    void getExplore_selectsFirstLineWithCourses() {
        // given: 코스 없는 노선을 빼면 데이터가 쌓일 때마다 칩이 늘어나 노선도가 흔들려 보인다
        lenient().when(courseQueryService.getMostLikedCourses(any(), any(), any())).thenReturn(emptyList());
        lenient().when(conceptTourQueryService.getConceptTours(isNull())).thenReturn(List.of());
        given(courseQueryService.getExploreLines()).willReturn(List.of(
                line(4L, "1호선", LineCode.LINE_1, false),
                line(9L, "2호선", LineCode.LINE_2, true)));
        given(courseQueryService.getLineCourses(any(), any(), any())).willReturn(List.of());

        // when
        ExploreResponse result = exploreQueryService.getExplore(null);

        // then: 비활성 노선은 목록에 남지만 선택되지는 않는다
        assertThat(result.lines()).hasSize(2);
        assertThat(result.selectedLineId()).isEqualTo(9L);
        verify(courseQueryService).getLineCourses(null, 9L, 3);
    }

    @Test
    @DisplayName("코스가 있는 노선이 하나도 없으면 선택 노선과 코스를 비운다")
    void getExplore_noLineHasCourses() {
        // given: 노선 칩은 뜨지만 전부 비활성인 초기 상태
        lenient().when(courseQueryService.getMostLikedCourses(any(), any(), any())).thenReturn(emptyList());
        lenient().when(conceptTourQueryService.getConceptTours(isNull())).thenReturn(List.of());
        given(courseQueryService.getExploreLines()).willReturn(List.of(
                line(4L, "1호선", LineCode.LINE_1, false)));

        // when
        ExploreResponse result = exploreQueryService.getExplore(null);

        // then: 선택할 노선이 없으면 코스 조회 자체를 하지 않는다
        assertThat(result.lines()).hasSize(1);
        assertThat(result.selectedLineId()).isNull();
        assertThat(result.lineCourses()).isEmpty();
        verify(courseQueryService, never()).getLineCourses(any(), any(), any());
    }

    @Test
    @DisplayName("노출할 노선이 아예 없으면 선택 노선과 코스를 비운다")
    void getExplore_noLines() {
        // given
        lenient().when(courseQueryService.getMostLikedCourses(any(), any(), any())).thenReturn(emptyList());
        lenient().when(conceptTourQueryService.getConceptTours(isNull())).thenReturn(List.of());
        given(courseQueryService.getExploreLines()).willReturn(List.of());

        // when
        ExploreResponse result = exploreQueryService.getExplore(null);

        // then
        assertThat(result.selectedLineId()).isNull();
        assertThat(result.lineCourses()).isEmpty();
        verify(courseQueryService, never()).getLineCourses(any(), any(), any());
    }
}
