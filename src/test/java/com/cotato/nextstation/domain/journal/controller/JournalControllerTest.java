package com.cotato.nextstation.domain.journal.controller;

import com.cotato.nextstation.domain.journal.service.command.JournalCommandService;
import com.cotato.nextstation.domain.journal.service.query.JournalQueryService;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import com.cotato.nextstation.global.jwt.JwtProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JournalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class JournalControllerTest {

    private static final String TOKEN = "access-token";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JournalCommandService journalCommandService;

    @MockitoBean
    JournalQueryService journalQueryService;

    @MockitoBean
    JwtProvider jwtProvider;

    @BeforeEach
    void authenticateAsMember1() {
        given(jwtProvider.parseClaims(TOKEN)).willReturn(
                Jwts.claims().subject("1").add("purpose", "ACCESS").build());
    }

    @Test
    @DisplayName("여행일지 상세는 토큰 없이 조회하고 memberId로 null을 넘긴다")
    void getJournalDetail_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/journals/10"))
                .andExpect(status().isOk());

        verify(journalQueryService).getJournalDetail(null, 10L);
    }

    @Test
    @DisplayName("여행일지 상세는 유효한 토큰이 있으면 회원 ID를 넘긴다")
    void getJournalDetail_withToken() throws Exception {
        mockMvc.perform(get("/api/v1/journals/10")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        verify(journalQueryService).getJournalDetail(1L, 10L);
    }

    @Test
    @DisplayName("선택적 인증이어도 위변조된 토큰을 보내면 401을 반환한다")
    void getJournalDetail_withInvalidToken() throws Exception {
        given(jwtProvider.parseClaims("invalid-token"))
                .willThrow(new MalformedJwtException("malformed"));

        mockMvc.perform(get("/api/v1/journals/10")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_TOKEN.getCode()));
    }
}
