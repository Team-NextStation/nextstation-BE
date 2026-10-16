package com.cotato.nextstation.domain.auth.controller;

import com.cotato.nextstation.domain.auth.dto.response.AppleLoginResponse;
import com.cotato.nextstation.domain.auth.exception.AuthErrorCode;
import com.cotato.nextstation.domain.auth.repository.AppleWebLoginCodeRepository;
import com.cotato.nextstation.domain.auth.service.result.AppleLoginResultType;
import com.cotato.nextstation.domain.member.entity.MemberRole;
import com.cotato.nextstation.global.exception.GlobalExceptionHandler;
import com.cotato.nextstation.global.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppleWebLoginExchangeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AppleWebLoginExchangeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AppleWebLoginCodeRepository appleWebLoginCodeRepository;

    @MockitoBean
    JwtProvider jwtProvider;

    @Test
    @DisplayName("유효한 code면 저장된 로그인 결과를 반환하고 code를 소비한다")
    void exchange_success() throws Exception {
        AppleLoginResponse response = new AppleLoginResponse(
                AppleLoginResultType.LOGIN_SUCCESS, 1L, "access-token", null, null, false, MemberRole.USER);
        given(appleWebLoginCodeRepository.consume("valid-code")).willReturn(Optional.of(response));

        mockMvc.perform(post("/api/v1/auth/apple/web/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"valid-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultType").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));

        then(appleWebLoginCodeRepository).should().consume("valid-code");
    }

    @Test
    @DisplayName("존재하지 않거나 이미 사용된 code면 401을 반환한다")
    void exchange_invalidCode_returnsUnauthorized() throws Exception {
        given(appleWebLoginCodeRepository.consume("used-code")).willReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/apple/web/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"used-code\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_APPLE_WEB_LOGIN_CODE.getCode()));
    }

    @Test
    @DisplayName("code가 비어 있으면 400을 반환한다")
    void exchange_blankCode_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/apple/web/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());

        then(appleWebLoginCodeRepository).shouldHaveNoInteractions();
    }
}
