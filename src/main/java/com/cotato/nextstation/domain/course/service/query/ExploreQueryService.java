package com.cotato.nextstation.domain.course.service.query;

import com.cotato.nextstation.domain.course.dto.response.ConceptTourResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreCourseResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreLineResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 둘러보기 메인 화면을 조합한다.
 * <p>
 * 화면에 섹션이 셋이라 프론트가 각각 호출하면 진입할 때마다 요청이 세 번 나간다.
 * 첫 화면이라 한 번에 내려주고, 더보기나 칩 전환부터 개별 API로 넘어간다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExploreQueryService {

    // 화면에 보이는 개수. 더보기로 들어가면 각 목록 API가 이어받는다.
    private static final int POPULAR_COURSE_COUNT = 6;
    private static final int CONCEPT_TOUR_COUNT = 3;
    private static final int LINE_COURSE_COUNT = 3;

    private final CourseQueryService courseQueryService;
    private final ConceptTourQueryService conceptTourQueryService;

    public ExploreResponse getExplore(Long memberId) {
        List<ExploreCourseResponse> popularCourses =
                courseQueryService.getMostLikedCourses(memberId, null, POPULAR_COURSE_COUNT).courses();

        List<ConceptTourResponse> conceptTours = conceptTourQueryService.getConceptTours(memberId).stream()
                .limit(CONCEPT_TOUR_COUNT)
                .toList();

        List<ExploreLineResponse> lines = courseQueryService.getExploreLines();
        Long selectedLineId = resolveSelectedLineId(lines);
        List<ExploreCourseResponse> lineCourses = resolveLineCourses(memberId, selectedLineId);

        return new ExploreResponse(popularCourses, conceptTours, lines, selectedLineId, lineCourses);
    }

    /**
     * 처음 선택해 둘 노선.
     * <p>
     * 코스가 있는 첫 노선을 고른다. 목록의 첫 번째를 그냥 쓰면 그 노선이 비활성일 때
     * 진입 화면이 빈 목록이 된다. 특정 호선을 고정하지 않는 것도 같은 이유다.
     */
    private Long resolveSelectedLineId(List<ExploreLineResponse> lines) {
        return lines.stream()
                .filter(ExploreLineResponse::hasCourses)
                .map(ExploreLineResponse::id)
                .findFirst()
                .orElse(null);
    }

    /**
     * 처음 보여줄 노선의 코스. 공개 코스가 있는 노선이 하나도 없으면 빈 목록이다.
     */
    private List<ExploreCourseResponse> resolveLineCourses(Long memberId, Long selectedLineId) {
        if (selectedLineId == null) {
            return List.of();
        }
        return courseQueryService.getLineCourses(memberId, selectedLineId, LINE_COURSE_COUNT);
    }
}
