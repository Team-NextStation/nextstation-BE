package com.cotato.nextstation.domain.recommendation.controller;

import com.cotato.nextstation.domain.recommendation.dto.request.CustomRecommendationRequest;
import com.cotato.nextstation.domain.recommendation.dto.response.CustomRecommendationResponse;
import com.cotato.nextstation.domain.recommendation.dto.response.RecommendedStationResponse;
import com.cotato.nextstation.domain.recommendation.enums.TravelTime;
import com.cotato.nextstation.domain.recommendation.exception.RecommendationErrorCode;
import com.cotato.nextstation.domain.recommendation.service.command.RecommendationCommandService;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import com.cotato.nextstation.domain.station.entity.LineCode;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomRecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CustomRecommendationControllerTest {

    private static final String SESSION_ID = "550e8400-e29b-41d4-a716-446655440000";

    private static final String TOKEN = "access-token";

    @Autowired
    MockMvc mockMvc;

    // @WebMvcTest 슬라이스에 ObjectMapper 빈이 노출되지 않아 요청 직렬화용으로 직접 생성한다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    RecommendationCommandService recommendationCommandService;

    // WebConfig가 등록하는 JwtPrincipalArgumentResolver가 필요로 해서 @WebMvcTest 슬라이스에도 목이 필요하다
    @MockitoBean
    JwtProvider jwtProvider;

    @BeforeEach
    void authenticateAsMember1() {
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
    }

    private CustomRecommendationRequest request() {
        return new CustomRecommendationRequest(SESSION_ID, 1L, TravelTime.THIRTY_MINUTES,
                List.of("NATURE", "BUDGET", "EXPERIENCE"));
    }

    private CustomRecommendationResponse sampleResponse() {
        return new CustomRecommendationResponse(
                new RecommendedStationResponse(10L, "보문역", "보문역 소개", List.of(),
                        List.of(new LineSummaryResponse(6L, "6호선", LineCode.LINE_6))),
                24);
    }

    @Test
    @DisplayName("로그인 사용자가 요청하면 토큰의 memberId로 호출되고 200과 추천 역을 반환한다")
    void recommendCustom_success() throws Exception {
        given(recommendationCommandService.recommendCustom(eq(1L), any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/recommendations/custom")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.station.stationName").value("보문역"))
                .andExpect(jsonPath("$.data.travelDurationMinutes").value(24));
    }

    @Test
    @DisplayName("토큰 없는 비로그인 요청은 memberId null로 호출되고 200을 반환한다")
    void recommendCustom_anonymous() throws Exception {
        given(recommendationCommandService.recommendCustom(isNull(), any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/recommendations/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.station.stationName").value("보문역"));
    }

    @Test
    @DisplayName("추천 세션 ID가 없으면 400을 반환한다")
    void recommendCustom_missingSessionId() throws Exception {
        CustomRecommendationRequest invalid = new CustomRecommendationRequest(
                null, 1L, TravelTime.ANY, List.of("NATURE"));

        mockMvc.perform(post("/api/v1/recommendations/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_VALIDATION_ERROR"));
        verify(recommendationCommandService, never()).recommendCustom(any(), any());
    }

    @Test
    @DisplayName("추천 세션 ID가 UUID 형식이 아니면 400을 반환한다")
    void recommendCustom_invalidSessionId() throws Exception {
        CustomRecommendationRequest invalid = new CustomRecommendationRequest(
                "not-a-uuid", 1L, TravelTime.ANY, List.of("NATURE"));

        mockMvc.perform(post("/api/v1/recommendations/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_VALIDATION_ERROR"));
        verify(recommendationCommandService, never()).recommendCustom(any(), any());
    }

    @Test
    @DisplayName("UUIDv7 추천 세션 ID를 허용한다")
    void recommendCustom_allowsUuidV7SessionId() throws Exception {
        CustomRecommendationRequest uuidV7Request = new CustomRecommendationRequest(
                "01890f9a-7b3c-7cc2-98c4-dc0c0c07398f", 1L, TravelTime.ANY, List.of("NATURE"));
        given(recommendationCommandService.recommendCustom(isNull(), any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/recommendations/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uuidV7Request)))
                .andExpect(status().isOk());

        verify(recommendationCommandService).recommendCustom(isNull(), any());
    }

    @Test
    @DisplayName("여행 스타일이 4개 이상이면 400을 반환한다")
    void recommendCustom_tooManyTravelStyles() throws Exception {
        CustomRecommendationRequest invalid = new CustomRecommendationRequest(
                SESSION_ID, 1L, TravelTime.ANY, List.of("NATURE", "BUDGET", "EXPERIENCE", "INDOOR"));

        mockMvc.perform(post("/api/v1/recommendations/custom")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        verify(recommendationCommandService, never()).recommendCustom(any(), any());
    }

    @Test
    @DisplayName("여행 스타일이 비어 있으면 400을 반환한다")
    void recommendCustom_emptyTravelStyles() throws Exception {
        CustomRecommendationRequest invalid = new CustomRecommendationRequest(
                SESSION_ID, 1L, TravelTime.ANY, List.of());

        mockMvc.perform(post("/api/v1/recommendations/custom")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        verify(recommendationCommandService, never()).recommendCustom(any(), any());
    }

    @Test
    @DisplayName("여행 스타일이 중복되면 400을 반환하고 서비스를 호출하지 않는다")
    void recommendCustom_duplicateTravelStyles() throws Exception {
        CustomRecommendationRequest invalid = new CustomRecommendationRequest(
                SESSION_ID, 1L, TravelTime.ANY, List.of("NATURE", "NATURE", "BUDGET"));

        mockMvc.perform(post("/api/v1/recommendations/custom")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.reasons.travelStyles").value("중복된 값은 넣을 수 없습니다."));
        verify(recommendationCommandService, never()).recommendCustom(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 여행 스타일이면 400을 반환하고 서비스를 호출하지 않는다")
    void recommendCustom_invalidTravelStyle() throws Exception {
        CustomRecommendationRequest invalid = new CustomRecommendationRequest(
                SESSION_ID, 1L, TravelTime.ANY, List.of("NATURE", "BUDGET", "NOT_A_TAG"));

        mockMvc.perform(post("/api/v1/recommendations/custom")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_400_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.reasons.travelStyles").value("존재하지 않는 여행 스타일입니다."));
        verify(recommendationCommandService, never()).recommendCustom(any(), any());
    }

    @Test
    @DisplayName("여행 스타일이 1~2개여도 정상 추천된다")
    void recommendCustom_allowsFewerThanThreeTravelStyles() throws Exception {
        CustomRecommendationRequest oneStyle = new CustomRecommendationRequest(
                SESSION_ID, 1L, TravelTime.ANY, List.of("NATURE"));
        given(recommendationCommandService.recommendCustom(eq(1L), any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/recommendations/custom")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oneStyle)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("도달 가능한 역이 없으면 404를 반환한다")
    void recommendCustom_noReachableStation() throws Exception {
        given(recommendationCommandService.recommendCustom(eq(1L), any()))
                .willThrow(new CustomException(RecommendationErrorCode.NO_REACHABLE_STATION));

        mockMvc.perform(post("/api/v1/recommendations/custom")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_404_NO_REACHABLE_STATION"));
    }
}
