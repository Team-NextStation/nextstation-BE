package com.cotato.nextstation.domain.course.controller;

import com.cotato.nextstation.domain.course.dto.response.PlaceCourseResponse;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.jwt.JwtProvider;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import com.cotato.nextstation.domain.station.entity.LineCode;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlaceCourseController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PlaceCourseControllerTest {

    private static final String TOKEN = "access-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CourseQueryService courseQueryService;

    // WebConfig가 등록하는 JwtPrincipalArgumentResolver가 필요로 해서 @WebMvcTest 슬라이스에도 목이 필요하다
    @MockitoBean
    JwtProvider jwtProvider;

    @BeforeEach
    void authenticateAsMember1() {
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
    }

    @Test
    @DisplayName("장소를 포함한 코스는 200과 코스 카드를 반환한다")
    void getCoursesByPlace_success() throws Exception {
        given(courseQueryService.getCoursesByPlace(1L)).willReturn(List.of(
                new PlaceCourseResponse(50L, "주연의 보문역 여행", 123L, "보문역",
                        new LineSummaryResponse(6L, "6호선", LineCode.LINE_6), 4, "SHORT", List.of("자연과함께", "사진찍기좋은"), "cover.jpg")));

        mockMvc.perform(get("/api/v1/places/{placeId}/courses", 1L)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].journalId").value(50))
                .andExpect(jsonPath("$.data[0].name").value("주연의 보문역 여행"))
                .andExpect(jsonPath("$.data[0].stationName").value("보문역"))
                .andExpect(jsonPath("$.data[0].line.id").value(6))
                .andExpect(jsonPath("$.data[0].line.name").value("6호선"))
                .andExpect(jsonPath("$.data[0].line.code").value("LINE_6"))
                .andExpect(jsonPath("$.data[0].placeCount").value(4))
                .andExpect(jsonPath("$.data[0].travelDuration").value("SHORT"))
                .andExpect(jsonPath("$.data[0].tags[0]").value("자연과함께"))
                .andExpect(jsonPath("$.data[0].imageUrl").value("cover.jpg"));
    }

    @Test
    @DisplayName("대표 호선이 없는 역의 코스는 line 필드가 생략되지 않고 null로 내려간다")
    void getCoursesByPlace_nullLine() throws Exception {
        given(courseQueryService.getCoursesByPlace(2L)).willReturn(List.of(
                new PlaceCourseResponse(11L, "코스", 200L, "역이름",
                        null, 3, "SHORT", List.of(), null)));

        mockMvc.perform(get("/api/v1/places/{placeId}/courses", 2L)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].line").hasJsonPath())
                .andExpect(jsonPath("$.data[0].line").value(nullValue()));
    }

    @Test
    @DisplayName("코스가 없으면 200과 빈 배열을 반환한다")
    void getCoursesByPlace_empty() throws Exception {
        given(courseQueryService.getCoursesByPlace(999L)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/places/{placeId}/courses", 999L)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401을 반환한다")
    void getCoursesByPlace_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/places/{placeId}/courses", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));
    }
}
