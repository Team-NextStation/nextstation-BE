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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StampCourseQueryService {

    private static final int POPULAR_COURSE_LIMIT = 3;

    private final StampCourseConverter stampCourseConverter;
    private final CourseQueryService courseQueryService;
    private final StationRepository stationRepository;
    private final StationLineRepository stationLineRepository;

    public StationPopularCoursesResponse getPopularCoursesByStation(Long memberId, Long stationId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new CustomException(StampErrorCode.STATION_NOT_FOUND));

        String lineName = stationLineRepository.findFirstByStation(station)
                .map(stationLine -> stationLine.getLine().getName())
                .orElse(null);

        List<PopularCourseResponse> courses =
                courseQueryService.getPopularCoursesByStation(stationId, POPULAR_COURSE_LIMIT, memberId);

        return stampCourseConverter.toStationPopularCoursesResponse(station, lineName, courses);
    }
}