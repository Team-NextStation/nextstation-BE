package com.cotato.nextstation.domain.stamp.controller;

import com.cotato.nextstation.domain.course.dto.response.PopularCourseResponse;
import com.cotato.nextstation.domain.stamp.dto.response.MyStampDetailResponse;
import com.cotato.nextstation.domain.stamp.dto.response.MyStampListResponse;
import com.cotato.nextstation.domain.stamp.dto.response.StampResponse;
import com.cotato.nextstation.domain.stamp.dto.response.StationPopularCoursesResponse;
import com.cotato.nextstation.domain.stamp.exception.StampErrorCode;
import com.cotato.nextstation.domain.stamp.service.command.StampCommandService;
import com.cotato.nextstation.domain.stamp.service.query.MemberStampQueryService;
import com.cotato.nextstation.domain.stamp.service.query.StampCourseQueryService;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import com.cotato.nextstation.domain.station.entity.LineCode;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.jwt.JwtProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StampCourseController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StampCourseControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    StampCommandService stampCommandService;
    @MockitoBean
    StampCourseQueryService stampCourseQueryService;
    @MockitoBean
    MemberStampQueryService memberStampQueryService;

    // WebConfig가 등록하는 JwtPrincipalArgumentResolver가 필요로 해서 @WebMvcTest 슬라이스에도 목이 필요하다
    @MockitoBean
    JwtProvider jwtProvider;

    private static final String TOKEN = "access-token";

    @Test
    @DisplayName("정상 accessToken이면 내 스탬프 목록을 반환한다")
    void getMyStamps_success() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());

        LineSummaryResponse line = new LineSummaryResponse(1L, "1호선", LineCode.LINE_1);
        StampResponse stamp = new StampResponse(573L, "제기동역", line);
        MyStampListResponse response = new MyStampListResponse(1, List.of(stamp));
        given(memberStampQueryService.getMyStamps(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/stamps")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.stamps[0].stationId").value(573L));
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401을 반환한다")
    void getMyStamps_missingAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/stamps"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("정상 accessToken이면 해당 역의 스탬프 상세를 반환한다")
    void getMyStampDetail_success() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());

        LineSummaryResponse line = new LineSummaryResponse(1L, "1호선", LineCode.LINE_1);
        MyStampDetailResponse response = new MyStampDetailResponse(
                5L, "제기동역", line, LocalDateTime.of(2026, 3, 2, 14, 20), 42L);
        given(memberStampQueryService.getMyStampDetail(1L, 5L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/stamps/{stationId}", 5L)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stationId").value(5L))
                .andExpect(jsonPath("$.data.stationName").value("제기동역"))
                .andExpect(jsonPath("$.data.line.id").value(1L))
                .andExpect(jsonPath("$.data.line.code").value("LINE_1"))
                .andExpect(jsonPath("$.data.journalId").value(42L));
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401을 반환한다")
    void getMyStampDetail_missingAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/stamps/{stationId}", 5L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("해당 역에 방문 기록이 없으면 404를 반환한다")
    void getMyStampDetail_notFound() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(memberStampQueryService.getMyStampDetail(1L, 5L))
                .willThrow(new CustomException(StampErrorCode.MEMBER_STAMP_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/stamps/{stationId}", 5L)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(StampErrorCode.MEMBER_STAMP_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("정상 accessToken이면 역별 인기 코스를 반환한다")
    void getPopularCoursesByStation_success() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());

        StationPopularCoursesResponse response = new StationPopularCoursesResponse(
                "보문역", "6호선", List.of(new PopularCourseResponse(1L, "보문역 코스", 300, 128, false)));
        given(stampCourseQueryService.getPopularCoursesByStation(1L, 12L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/stamps/stations/{stationId}/courses", 12L)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stationName").value("보문역"))
                .andExpect(jsonPath("$.data.courses[0].courseId").value(1L));
    }

    // 이 엔드포인트는 차단 필터링용 viewer id를 연결하며 @AuthenticationPrincipal이 처음 추가됐다.
    // SecurityConfig가 anyRequest().permitAll()이라 인증 강제는 오직 이 파라미터 존재 여부에만
    // 의존하므로, 토큰 없이 호출했을 때 실제로 401이 뜨는지 여기서 고정해 둔다.
    @Test
    @DisplayName("Authorization 헤더가 없으면 401을 반환한다")
    void getPopularCoursesByStation_missingAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/stamps/stations/{stationId}/courses", 12L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));
    }
}
