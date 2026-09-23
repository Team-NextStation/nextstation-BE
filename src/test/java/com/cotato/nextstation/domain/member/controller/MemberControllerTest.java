package com.cotato.nextstation.domain.member.controller;

import com.cotato.nextstation.domain.auth.util.RefreshTokenCookieFactory;
import com.cotato.nextstation.domain.course.dto.response.MemberCourseCardResponse;
import com.cotato.nextstation.domain.course.dto.response.MemberCourseListResponse;
import com.cotato.nextstation.domain.course.service.query.CourseQueryService;
import com.cotato.nextstation.domain.member.dto.response.MemberProfileResponse;
import com.cotato.nextstation.domain.member.dto.response.OtherMemberProfileResponse;
import com.cotato.nextstation.domain.member.exception.MemberErrorCode;
import com.cotato.nextstation.domain.member.service.MemberWithdrawService;
import com.cotato.nextstation.domain.member.service.command.MemberCommandService;
import com.cotato.nextstation.domain.member.service.query.MemberQueryService;
import com.cotato.nextstation.domain.stamp.dto.response.MemberStampListResponse;
import com.cotato.nextstation.domain.stamp.dto.response.MemberStampResponse;
import com.cotato.nextstation.domain.stamp.service.query.MemberStampQueryService;
import com.cotato.nextstation.domain.station.dto.response.LineSummaryResponse;
import com.cotato.nextstation.domain.station.entity.LineCode;
import com.cotato.nextstation.global.exception.CustomException;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.jwt.JwtProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MemberControllerTest {

    private static final String TOKEN = "access-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MemberQueryService memberQueryService;

    @MockitoBean
    MemberCommandService memberCommandService;

    @MockitoBean
    MemberStampQueryService memberStampQueryService;

    @MockitoBean
    CourseQueryService courseQueryService;

    @MockitoBean
    MemberWithdrawService memberWithdrawService;

    // 생성자에서 @Value 프로퍼티를 받아 슬라이스 컨텍스트에서는 생성되지 않는다
    @MockitoBean
    RefreshTokenCookieFactory refreshTokenCookieFactory;

    // WebConfig가 등록하는 JwtPrincipalArgumentResolver가 필요로 해서 @WebMvcTest 슬라이스에도 목이 필요하다
    @MockitoBean
    JwtProvider jwtProvider;

    @Test
    @DisplayName("정상 accessToken이면 닉네임/프로필 이미지를 반환한다")
    void getMyProfile_success() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(memberQueryService.getMyProfile(1L))
                .willReturn(new MemberProfileResponse(1L, "환승러", "https://cdn.example.com/profile/1.png"));

        // when & then
        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(1L))
                .andExpect(jsonPath("$.data.nickname").value("환승러"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.example.com/profile/1.png"));
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401을 반환한다")
    void getMyProfile_missingAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 404를 반환한다")
    void getMyProfile_memberNotFound() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(memberQueryService.getMyProfile(1L))
                .willThrow(new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(MemberErrorCode.MEMBER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("정상 accessToken이면 넘어온 필드만 프로필을 수정한다")
    void updateMyProfile_success() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(memberCommandService.updateMyProfile(1L, "새닉네임", null))
                .willReturn(new MemberProfileResponse(1L, "새닉네임", null));

        // when & then
        mockMvc.perform(patch("/api/v1/members/me")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401을 반환한다")
    void updateMyProfile_missingAuthorizationHeader() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새닉네임\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("탈퇴에 성공하면 refreshToken 쿠키를 즉시 만료시킨다")
    void withdraw_success() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(refreshTokenCookieFactory.createExpired())
                .willReturn(ResponseCookie.from("refreshToken", "").path("/").maxAge(0).build());

        // when & then
        mockMvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        then(memberWithdrawService).should().withdraw(1L);
    }

    @Test
    @DisplayName("탈퇴 시 Authorization 헤더가 없으면 401을 반환한다")
    void withdraw_missingAuthorizationHeader() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));

        then(memberWithdrawService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("다른 회원 프로필 조회 성공 시 닉네임/프로필 이미지/스탬프 개수/공개 코스 개수를 반환한다")
    void getMemberProfile_success() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(memberQueryService.getMemberProfile(1L, 2L))
                .willReturn(new OtherMemberProfileResponse(
                        2L, "환승러2", "https://cdn.example.com/profile/2.png", 12L, 5L));

        // when & then
        mockMvc.perform(get("/api/v1/members/2/profile")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(2L))
                .andExpect(jsonPath("$.data.nickname").value("환승러2"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.example.com/profile/2.png"))
                .andExpect(jsonPath("$.data.stampCount").value(12))
                .andExpect(jsonPath("$.data.publicCourseCount").value(5));
    }

    @Test
    @DisplayName("다른 회원 프로필 조회 시 memberId가 0이면 400을 반환한다")
    void getMemberProfile_memberIdZero() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());

        // when & then
        mockMvc.perform(get("/api/v1/members/0/profile")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("다른 회원 프로필 조회 시 memberId가 음수이면 400을 반환한다")
    void getMemberProfile_memberIdNegative() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());

        // when & then
        mockMvc.perform(get("/api/v1/members/-1/profile")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("다른 회원 프로필 조회 시 Authorization 헤더가 없으면 401을 반환한다")
    void getMemberProfile_missingAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/members/2/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("존재하지 않는 회원의 프로필을 조회하면 404를 반환한다")
    void getMemberProfile_memberNotFound() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(memberQueryService.getMemberProfile(1L, 2L))
                .willThrow(new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/members/2/profile")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(MemberErrorCode.MEMBER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("다른 회원 스탬프 목록은 200과 스탬프 개수/역 목록을 반환한다")
    void getMemberStamps_success() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(memberStampQueryService.getMemberStamps(1L, 2L)).willReturn(new MemberStampListResponse(
                1, List.of(new MemberStampResponse(6L, "보문역",
                        new LineSummaryResponse(6L, "6호선", LineCode.LINE_6)))));

        // when & then
        mockMvc.perform(get("/api/v1/members/2/stamps").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stampCount").value(1))
                .andExpect(jsonPath("$.data.stamps[0].stationId").value(6L))
                .andExpect(jsonPath("$.data.stamps[0].stationName").value("보문역"));
    }

    @Test
    @DisplayName("다른 회원 스탬프 목록 조회 시 memberId가 0이면 400을 반환한다")
    void getMemberStamps_memberIdZero() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());

        // when & then
        mockMvc.perform(get("/api/v1/members/0/stamps")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("다른 회원 스탬프 목록 조회 시 memberId가 음수이면 400을 반환한다")
    void getMemberStamps_memberIdNegative() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());

        // when & then
        mockMvc.perform(get("/api/v1/members/-1/stamps")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("다른 회원 스탬프 목록 조회 시 Authorization 헤더가 없으면 401을 반환한다")
    void getMemberStamps_missingAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/members/2/stamps"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("존재하지 않는 회원의 스탬프 목록을 조회하면 404를 반환한다")
    void getMemberStamps_memberNotFound() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(memberStampQueryService.getMemberStamps(1L, 2L))
                .willThrow(new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/members/2/stamps").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(MemberErrorCode.MEMBER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("다른 회원 공개 코스 목록은 200과 코스 카드/다음 커서를 반환한다")
    void getMemberCourses_success() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(courseQueryService.getMemberPublicCourses(1L, 2L, null, null)).willReturn(
                new MemberCourseListResponse(
                        List.of(new MemberCourseCardResponse(7L, 20L, "보문역 환승여행 코스", 6L, "보문역",
                                new LineSummaryResponse(6L, "6호선", LineCode.LINE_6),
                                "https://image.example.com/journal.jpg", 12)),
                        "eyJpZCI6MjB9", true));

        // when & then
        mockMvc.perform(get("/api/v1/members/2/courses").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courses[0].courseId").value(7L))
                .andExpect(jsonPath("$.data.courses[0].journalId").value(20L))
                .andExpect(jsonPath("$.data.courses[0].stationName").value("보문역"))
                .andExpect(jsonPath("$.data.courses[0].imageUrl").value("https://image.example.com/journal.jpg"))
                .andExpect(jsonPath("$.data.courses[0].likeCount").value(12))
                .andExpect(jsonPath("$.data.nextCursor").value("eyJpZCI6MjB9"))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    @DisplayName("다른 회원 공개 코스 목록 조회 시 memberId가 0이면 400을 반환한다")
    void getMemberCourses_memberIdZero() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());

        // when & then
        mockMvc.perform(get("/api/v1/members/0/courses")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("다른 회원 공개 코스 목록 조회 시 memberId가 음수이면 400을 반환한다")
    void getMemberCourses_memberIdNegative() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());

        // when & then
        mockMvc.perform(get("/api/v1/members/-1/courses")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("다른 회원 공개 코스 목록 조회 시 Authorization 헤더가 없으면 401을 반환한다")
    void getMemberCourses_missingAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/members/2/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CLIENT_ERROR_401_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("존재하지 않는 회원의 공개 코스 목록을 조회하면 404를 반환한다")
    void getMemberCourses_memberNotFound() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        given(courseQueryService.getMemberPublicCourses(1L, 2L, null, null))
                .willThrow(new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/members/2/courses").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(MemberErrorCode.MEMBER_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("다른 회원 공개 코스 목록 조회 시 size 범위를 벗어나면 400을 반환한다")
    void getMemberCourses_invalidPageSize() throws Exception {
        // given
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
        willThrow(new CustomException(GlobalErrorCode.INVALID_PAGE_SIZE))
                .given(courseQueryService).getMemberPublicCourses(1L, 2L, null, 100);

        // when & then
        mockMvc.perform(get("/api/v1/members/2/courses")
                        .header("Authorization", "Bearer " + TOKEN)
                        .param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_PAGE_SIZE.getCode()));
    }
}
