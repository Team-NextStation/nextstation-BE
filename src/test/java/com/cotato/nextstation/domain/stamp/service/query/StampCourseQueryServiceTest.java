package com.cotato.nextstation.domain.stamp.service.query;

import com.cotato.nextstation.domain.course.dto.response.PopularCourseResponse;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.domain.stamp.converter.StampCourseConverter;
import com.cotato.nextstation.domain.stamp.dto.response.StationPopularCoursesResponse;
import com.cotato.nextstation.domain.stamp.exception.StampErrorCode;
import com.cotato.nextstation.domain.station.entity.Station;
import com.cotato.nextstation.domain.station.repository.StationLineRepository;
import com.cotato.nextstation.domain.station.repository.StationRepository;
import com.cotato.nextstation.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class StampCourseQueryServiceTest {

    @InjectMocks
    private StampCourseQueryService stampCourseQueryService;

    @Mock
    private StationRepository stationRepository;
    @Mock
    private StationLineRepository stationLineRepository;
    @Mock
    private CourseQueryService courseQueryService;
    @Mock
    private StampCourseConverter stampCourseConverter;

    @Test
    @DisplayName("역별 인기 코스를 상위 3개까지 조회한다")
    void getPopularCoursesByStation_success() {
        // given
        Long stationId = 12L;
        Station station = mock(Station.class);

        List<PopularCourseResponse> courses = List.of(
                new PopularCourseResponse(1L, "보문역 코스", 300, 128, false)
        );

        StationPopularCoursesResponse expected =
                new StationPopularCoursesResponse("보문역", "6호선", courses);

        given(stationRepository.findById(stationId)).willReturn(Optional.of(station));
        given(stationLineRepository.findFirstByStation(station)).willReturn(Optional.empty());
        given(courseQueryService.getPopularCoursesByStation(eq(stationId), eq(3), eq(1L))).willReturn(courses);

        given(stampCourseConverter.toStationPopularCoursesResponse(eq(station), any(), eq(courses)))
                .willReturn(expected);

        // when
        StationPopularCoursesResponse result = stampCourseQueryService.getPopularCoursesByStation(1L, stationId);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("존재하지 않는 역이면 예외가 발생한다")
    void getPopularCoursesByStation_stationNotFound() {
        // given
        Long stationId = 999L;
        given(stationRepository.findById(stationId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> stampCourseQueryService.getPopularCoursesByStation(1L, stationId))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(StampErrorCode.STATION_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("인기 코스가 없으면 빈 목록을 반환한다")
    void getPopularCoursesByStation_empty() {
        // given
        Long stationId = 999L;
        Station station = mock(Station.class);


        StationPopularCoursesResponse expected = new StationPopularCoursesResponse("보문역", null, List.of());
        given(stationRepository.findById(stationId)).willReturn(Optional.of(station));
        given(stationLineRepository.findFirstByStation(station)).willReturn(Optional.empty());
        given(courseQueryService.getPopularCoursesByStation(eq(stationId), eq(3), eq(1L))).willReturn(List.of());
        given(stampCourseConverter.toStationPopularCoursesResponse(eq(station), any(), eq(List.of())))
                .willReturn(expected);

        // when
        StationPopularCoursesResponse result = stampCourseQueryService.getPopularCoursesByStation(1L, stationId);

        // then
        assertThat(result.courses()).isEmpty();
    }
}