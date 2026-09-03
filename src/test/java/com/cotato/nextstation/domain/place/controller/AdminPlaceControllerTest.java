package com.cotato.nextstation.domain.place.controller;

import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCardResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceDetailResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceListResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminStationSummaryResponse;
import com.cotato.nextstation.domain.place.enums.CategoryCode;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.service.query.AdminPlaceQueryService;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import com.cotato.nextstation.domain.station.entity.LineCode;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPlaceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminPlaceControllerTest {

    private static final String TOKEN = "access-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminPlaceQueryService adminPlaceQueryService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @BeforeEach
    void authenticateAsMember1() {
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
    }

    @Test
    @DisplayName("관리자 장소 목록은 필터 선택지와 카드 정보를 반환한다")
    void getPlaces_success() throws Exception {
        LineSummaryResponse line = new LineSummaryResponse(3L, "3호선", LineCode.LINE_3);
        AdminPlaceCardResponse card = new AdminPlaceCardResponse(
                7L, line, 10L, "신림역", "CAFE", "카페", "장소명",
                List.of("INDOOR", "BUDGET"), "한 줄 설명", "image-1", PlaceStatus.APPROVED);
        given(adminPlaceQueryService.getPlaces(
                1L, 3L, 10L, CategoryCode.CAFE, PlaceStatus.APPROVED, null, 10))
                .willReturn(new AdminPlaceListResponse(
                        List.of(line), List.of(new AdminStationSummaryResponse(10L, "신림역")),
                        List.of(card), null, false));

        mockMvc.perform(get("/api/v1/admin/places")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("lineId", "3")
                        .param("stationId", "10")
                        .param("categoryCode", "CAFE")
                        .param("status", "APPROVED")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableLines[0].id").value(3))
                .andExpect(jsonPath("$.data.availableStations[0].stationId").value(10))
                .andExpect(jsonPath("$.data.places[0].representativeLine.code").value("LINE_3"))
                .andExpect(jsonPath("$.data.places[0].tags[1]").value("BUDGET"))
                .andExpect(jsonPath("$.data.places[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("검색어가 없어도 200과 빈 목록을 반환한다")
    void searchPlaces_blankKeyword() throws Exception {
        given(adminPlaceQueryService.searchPlaces(1L, null)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/places/search")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("삭제 장소 상세에는 삭제 사유가 반환된다")
    void getPlaceDetail_deletedPlace() throws Exception {
        given(adminPlaceQueryService.getPlaceDetail(1L, 7L)).willReturn(
                new AdminPlaceDetailResponse(
                        7L, "삭제 장소", null, 10L, "신림역", PlaceStatus.DELETED,
                        "CAFE", "카페", List.of("INDOOR"), "설명", List.of(),
                        "폐업", null));

        mockMvc.perform(get("/api/v1/admin/places/{placeId}", 7L)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"))
                .andExpect(jsonPath("$.data.deleteReason").value("폐업"))
                .andExpect(jsonPath("$.data.rejectReason").isEmpty());
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401이고 서비스를 호출하지 않는다")
    void getPlaces_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/places"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));

        verify(adminPlaceQueryService, never()).getPlaces(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("일반 회원이면 AdminGuard의 403을 반환한다")
    void getPlaces_forbidden() throws Exception {
        willThrow(new CustomException(GlobalErrorCode.FORBIDDEN))
                .given(adminPlaceQueryService).getPlaces(1L, null, null, null, null, null, null);

        mockMvc.perform(get("/api/v1/admin/places")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_403_FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 필터는 400이다")
    void getPlaces_invalidCategory() throws Exception {
        mockMvc.perform(get("/api/v1/admin/places")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("categoryCode", "UNKNOWN"))
                .andExpect(status().isBadRequest());

        verify(adminPlaceQueryService, never()).getPlaces(any(), any(), any(), any(), any(), any(), any());
    }
}
