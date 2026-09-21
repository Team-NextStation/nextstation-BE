package com.cotato.nextstation.domain.course.controller;

import com.cotato.nextstation.domain.course.dto.response.ConceptTourResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreCourseListResponse;
import com.cotato.nextstation.domain.course.entity.CourseSort;
import com.cotato.nextstation.domain.course.service.query.ConceptTourQueryService;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.jwt.JwtProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConceptTourController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ConceptTourControllerTest {

    private static final String TOKEN = "access-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConceptTourQueryService conceptTourQueryService;

    @MockitoBean
    CourseQueryService courseQueryService;

    // WebConfig가 등록하는 JwtPrincipalArgumentResolver가 필요로 해서 @WebMvcTest 슬라이스에도 목이 필요하다
    @MockitoBean
    JwtProvider jwtProvider;

    @BeforeEach
    void authenticateAsMember1() {
        // 리졸버가 토큰에서 memberId를 꺼내므로, 토큰을 실은 요청은 1번 회원으로 인증된 것처럼 둔다
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
    }

    @Test
    @DisplayName("컨셉 목록은 코스 수와 함께 반환한다")
    void getConceptTours_success() throws Exception {
        given(conceptTourQueryService.getConceptTours()).willReturn(List.of(
                new ConceptTourResponse(1L, "문구 투어", "작은 문구점과 책방을 찾아가는 코스", 18)));

        mockMvc.perform(get("/api/v1/explore/concept-tours")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].conceptTourId").value(1))
                .andExpect(jsonPath("$.data[0].name").value("문구 투어"))
                .andExpect(jsonPath("$.data[0].courseCount").value(18));
    }

    @Test
    @DisplayName("컨셉 목록은 토큰 없이도 조회할 수 있다")
    void getConceptTours_withoutToken() throws Exception {
        given(conceptTourQueryService.getConceptTours()).willReturn(List.of());

        mockMvc.perform(get("/api/v1/explore/concept-tours"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("컨셉별 코스는 컨셉 조건만 걸어 둘러보기 조회를 재사용한다")
    void getConceptTourCourses_usesConceptCondition() throws Exception {
        given(courseQueryService.getConceptTourCourses(eq(1L), any(), any(), any(), any()))
                .willReturn(new ExploreCourseListResponse(List.of(), List.of(), null, false));

        mockMvc.perform(get("/api/v1/explore/concept-tours/{conceptTourId}/courses", 1L)
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("sort", "POPULAR")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(courseQueryService).getConceptTourCourses(1L, 1L, CourseSort.POPULAR, null, 5);
    }

    @Test
    @DisplayName("컨셉별 코스는 토큰 없이도 조회하고 memberId로 null을 넘긴다")
    void getConceptTourCourses_withoutToken() throws Exception {
        given(courseQueryService.getConceptTourCourses(eq(null), eq(1L), any(), any(), any()))
                .willReturn(new ExploreCourseListResponse(List.of(), List.of(), null, false));

        mockMvc.perform(get("/api/v1/explore/concept-tours/{conceptTourId}/courses", 1L))
                .andExpect(status().isOk());

        verify(courseQueryService).getConceptTourCourses(eq(null), eq(1L), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("정렬을 생략하면 서비스가 기본값을 정하도록 null을 넘긴다")
    void getConceptTourCourses_defaultSort() throws Exception {
        given(courseQueryService.getConceptTourCourses(any(), any(), any(), any(), any()))
                .willReturn(new ExploreCourseListResponse(List.of(), List.of(), null, false));

        mockMvc.perform(get("/api/v1/explore/concept-tours/{conceptTourId}/courses", 1L)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        verify(courseQueryService).getConceptTourCourses(eq(1L), eq(1L), isNull(), isNull(), isNull());
    }
}
