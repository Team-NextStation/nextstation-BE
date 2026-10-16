package com.cotato.nextstation.domain.place.controller;

import com.cotato.nextstation.domain.place.dto.request.AdminPlaceStatusUpdateRequest;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceCardResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceDetailResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceListResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceStatusUpdateResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceImageResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminPlaceTagResponse;
import com.cotato.nextstation.domain.place.dto.response.AdminStationSummaryResponse;
import com.cotato.nextstation.domain.place.enums.CategoryCode;
import com.cotato.nextstation.domain.place.enums.PlaceStatus;
import com.cotato.nextstation.domain.place.enums.PlaceTagName;
import com.cotato.nextstation.domain.place.service.command.AdminPlaceCommandService;
import com.cotato.nextstation.domain.place.service.query.AdminPlaceQueryService;
import com.cotato.nextstation.domain.place.service.query.KakaoPlaceSearchService;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import com.cotato.nextstation.domain.station.entity.LineCode;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPlaceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminPlaceControllerTest {

    private static final String TOKEN = "access-token";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AdminPlaceQueryService adminPlaceQueryService;

    @MockitoBean
    private KakaoPlaceSearchService kakaoPlaceSearchService;

    @MockitoBean
    private AdminPlaceCommandService adminPlaceCommandService;

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
                1L, 3L, 10L, CategoryCode.CAFE, List.of(PlaceStatus.APPROVED), null, 10))
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
    @DisplayName("관리자 장소 목록은 커서를 서비스에 전달하고 다음 커서 정보를 반환한다")
    void getPlaces_withCursor() throws Exception {
        String cursor = "current-cursor";
        String nextCursor = "next-cursor";
        given(adminPlaceQueryService.getPlaces(
                1L, null, null, null, null, cursor, 2))
                .willReturn(new AdminPlaceListResponse(
                        List.of(), List.of(), List.of(), nextCursor, true));

        mockMvc.perform(get("/api/v1/admin/places")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("cursor", cursor)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").value(nextCursor))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        verify(adminPlaceQueryService).getPlaces(
                1L, null, null, null, null, cursor, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "51"})
    @DisplayName("페이지 크기가 1~50 범위를 벗어나면 서비스 호출 없이 400을 반환한다")
    void getPlaces_invalidSize(String size) throws Exception {
        mockMvc.perform(get("/api/v1/admin/places")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_ERROR.getCode()));

        verify(adminPlaceQueryService, never()).getPlaces(any(), any(), any(), any(), any(), any(), any());
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
    @DisplayName("관리자 장소 활성 태그 선택지는 코드와 표시명을 반환한다")
    void getActiveTags_success() throws Exception {
        given(adminPlaceQueryService.getActiveTags(1L)).willReturn(List.of(
                new AdminPlaceTagResponse(PlaceTagName.HOTPLACE, "핫플레이스")));

        mockMvc.perform(get("/api/v1/admin/places/tags")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tagName").value("HOTPLACE"))
                .andExpect(jsonPath("$.data[0].label").value("핫플레이스"));
    }

    @Test
    @DisplayName("삭제 장소 상세에는 삭제 사유가 반환된다")
    void getPlaceDetail_deletedPlace() throws Exception {
        given(adminPlaceQueryService.getPlaceDetail(1L, 7L)).willReturn(
                new AdminPlaceDetailResponse(
                        7L, "삭제 장소", null, 10L, "신림역",
                        "서울 용산구 남영동 72-1", 126.972123, 37.544321,
                        "https://place.map.kakao.com/123456789",
                        PlaceStatus.DELETED,
                        "CAFE", "카페", List.of("INDOOR"), "설명", List.of(),
                        "폐업", null));

        mockMvc.perform(get("/api/v1/admin/places/{placeId}", 7L)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"))
                .andExpect(jsonPath("$.data.address").value("서울 용산구 남영동 72-1"))
                .andExpect(jsonPath("$.data.xCoordinate").value(126.972123))
                .andExpect(jsonPath("$.data.yCoordinate").value(37.544321))
                .andExpect(jsonPath("$.data.kakaoPlaceUrl")
                        .value("https://place.map.kakao.com/123456789"))
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

    @Test
    @DisplayName("휴지통은 반려와 삭제 상태를 함께 조회한다")
    void getPlaces_multipleStatuses() throws Exception {
        given(adminPlaceQueryService.getPlaces(
                1L, null, null, null, List.of(PlaceStatus.REJECTED, PlaceStatus.DELETED), null, null))
                .willReturn(new AdminPlaceListResponse(List.of(), List.of(), List.of(), null, false));

        mockMvc.perform(get("/api/v1/admin/places")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("status", "REJECTED")
                        .param("status", "DELETED"))
                .andExpect(status().isOk());

        verify(adminPlaceQueryService).getPlaces(
                1L, null, null, null, List.of(PlaceStatus.REJECTED, PlaceStatus.DELETED), null, null);
    }

    @Test
    @DisplayName("상태 변경은 변경된 장소 ID와 상태를 반환한다")
    void updatePlaceStatus_success() throws Exception {
        given(adminPlaceCommandService.updateStatus(eq(1L), eq(7L), any(AdminPlaceStatusUpdateRequest.class)))
                .willReturn(new AdminPlaceStatusUpdateResponse(7L, PlaceStatus.REJECTED));

        mockMvc.perform(patch("/api/v1/admin/places/{placeId}/status", 7L)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminPlaceStatusUpdateRequest(PlaceStatus.REJECTED, "사진이 기준에 맞지 않음"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.placeId").value(7))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("변경할 상태가 없으면 서비스를 호출하지 않고 400을 반환한다")
    void updatePlaceStatus_statusRequired() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/places/{placeId}/status", 7L)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"사유\"}"))
                .andExpect(status().isBadRequest());

        verify(adminPlaceCommandService, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("사유가 255자를 넘으면 서비스를 호출하지 않고 400을 반환한다")
    void updatePlaceStatus_reasonTooLong() throws Exception {
        String reason = "가".repeat(256);

        mockMvc.perform(patch("/api/v1/admin/places/{placeId}/status", 7L)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminPlaceStatusUpdateRequest(PlaceStatus.REJECTED, reason))))
                .andExpect(status().isBadRequest());

        verify(adminPlaceCommandService, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("관리자 장소 수정은 변경된 상세 정보와 이미지 ID를 반환한다")
    void updatePlace_success() throws Exception {
        given(adminPlaceCommandService.updatePlace(eq(1L), eq(7L), any())).willReturn(
                new AdminPlaceDetailResponse(
                        7L, "장소", null, 10L, "신림역", "서울시", 127.0, 37.0,
                        "https://place.map.kakao.com/123", PlaceStatus.APPROVED,
                        "CAFE", "카페", List.of("HOTPLACE", "INDOOR"), "새 설명",
                        List.of(new AdminPlaceImageResponse(21L, "image-url")), null, null));

        mockMvc.perform(patch("/api/v1/admin/places/{placeId}", 7L)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tagNames": ["HOTPLACE", "INDOOR"],
                                  "description": "새 설명",
                                  "imageUrls": ["https://bucket/new.jpg"],
                                  "deleteImageIds": [11]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("새 설명"))
                .andExpect(jsonPath("$.data.images[0].imageId").value(21))
                .andExpect(jsonPath("$.data.images[0].imageUrl").value("image-url"));
    }

    @Test
    @DisplayName("관리자 장소 수정 요청에 변경값이 없으면 400이다")
    void updatePlace_emptyRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/places/{placeId}", 7L)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_ERROR.getCode()));

        verify(adminPlaceCommandService, never()).updatePlace(any(), any(), any());
    }

    @Test
    @DisplayName("관리자 장소 수정 태그가 서로 다른 2개가 아니면 400이다")
    void updatePlace_invalidTags() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/places/{placeId}", 7L)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagNames": ["HOTPLACE", "HOTPLACE"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_ERROR.getCode()));

        verify(adminPlaceCommandService, never()).updatePlace(any(), any(), any());
    }
}
