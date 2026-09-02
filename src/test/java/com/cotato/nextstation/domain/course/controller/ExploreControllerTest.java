package com.cotato.nextstation.domain.course.controller;

import com.cotato.nextstation.domain.course.dto.request.ExploreCourseCondition;
import com.cotato.nextstation.domain.course.dto.response.ExploreCourseListResponse;
import com.cotato.nextstation.domain.course.dto.response.ExploreResponse;
import com.cotato.nextstation.domain.course.entity.CourseSort;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.domain.course.service.query.ExploreQueryService;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.jwt.JwtProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExploreController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ExploreControllerTest {

    private static final String TOKEN = "access-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ExploreQueryService exploreQueryService;

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
    @DisplayName("둘러보기 메인은 토큰 없이도 조회하고 memberId로 null을 넘긴다")
    void getExplore_withoutToken() throws Exception {
        given(exploreQueryService.getExplore(null))
                .willReturn(new ExploreResponse(List.of(), List.of(), List.of(), null, List.of()));

        mockMvc.perform(get("/api/v1/explore"))
                .andExpect(status().isOk());

        verify(exploreQueryService).getExplore(null);
    }

    @Test
    @DisplayName("둘러보기 목록은 토큰 없이도 조회하고 memberId로 null을 넘긴다")
    void getExploreCourses_withoutToken() throws Exception {
        given(courseQueryService.getExploreCourses(eq(null), any(), any(), any(), any()))
                .willReturn(new ExploreCourseListResponse(List.of(), List.of(), null, false));

        mockMvc.perform(get("/api/v1/explore/courses"))
                .andExpect(status().isOk());

        verify(courseQueryService).getExploreCourses(eq(null), any(), eq(null), eq(null), eq(null));
    }

    @Test
    @DisplayName("둘러보기 목록은 토큰이 있으면 200을 반환한다")
    void getExploreCourses_withToken() throws Exception {
        given(courseQueryService.getExploreCourses(eq(1L), any(), any(), any(), any()))
                .willReturn(new ExploreCourseListResponse(List.of(), List.of(), null, false));

        mockMvc.perform(get("/api/v1/explore/courses")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses").isArray())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("둘러보기 목록은 필터·검색·정렬을 그대로 서비스에 넘긴다")
    void getExploreCourses_passesParameters() throws Exception {
        given(courseQueryService.getExploreCourses(eq(1L), any(), eq(CourseSort.POPULAR), eq("cursor-value"), eq(5)))
                .willReturn(new ExploreCourseListResponse(List.of(), List.of(), null, false));

        mockMvc.perform(get("/api/v1/explore/courses")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("lineId", "2")
                        .param("stationId", "123")
                        .param("keyword", "신림")
                        .param("sort", "POPULAR")
                        .param("cursor", "cursor-value")
                        .param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<ExploreCourseCondition> conditionCaptor =
                ArgumentCaptor.forClass(ExploreCourseCondition.class);
        verify(courseQueryService).getExploreCourses(
                eq(1L), conditionCaptor.capture(), eq(CourseSort.POPULAR), eq("cursor-value"), eq(5));
        assertThat(conditionCaptor.getValue())
                .isEqualTo(new ExploreCourseCondition(2L, 123L, "신림", null));
    }

    @Test
    @DisplayName("커서가 정렬과 맞지 않으면 400을 반환한다")
    void getExploreCourses_invalidCursor() throws Exception {
        given(courseQueryService.getExploreCourses(any(), any(), any(), any(), any()))
                .willThrow(new CustomException(GlobalErrorCode.INVALID_CURSOR));

        mockMvc.perform(get("/api/v1/explore/courses")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("cursor", "broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_CURSOR.getCode()));
    }

    @Test
    @DisplayName("많이 찾는 코스는 토큰 없이도 조회하고 memberId로 null을 넘긴다")
    void getMostLikedCourses_withoutToken() throws Exception {
        given(courseQueryService.getMostLikedCourses(null, null, null))
                .willReturn(new ExploreCourseListResponse(List.of(), List.of(), null, false));

        mockMvc.perform(get("/api/v1/explore/courses/popular"))
                .andExpect(status().isOk());

        verify(courseQueryService).getMostLikedCourses(null, null, null);
    }

    @Test
    @DisplayName("선택적 인증 API도 위변조된 토큰을 보내면 401을 반환한다")
    void getExploreCourses_withInvalidToken() throws Exception {
        given(jwtProvider.parseClaims("invalid-token"))
                .willThrow(new MalformedJwtException("malformed"));

        mockMvc.perform(get("/api/v1/explore/courses")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_TOKEN.getCode()));
    }

    @Test
    @DisplayName("많이 찾는 코스는 커서와 size를 그대로 서비스에 넘긴다")
    void getMostLikedCourses_passesParameters() throws Exception {
        given(courseQueryService.getMostLikedCourses(eq(1L), eq("cursor-value"), eq(6)))
                .willReturn(new ExploreCourseListResponse(List.of(), List.of(), null, false));

        mockMvc.perform(get("/api/v1/explore/courses/popular")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("cursor", "cursor-value")
                        .param("size", "6"))
                .andExpect(status().isOk());

        verify(courseQueryService).getMostLikedCourses(1L, "cursor-value", 6);
    }
}
