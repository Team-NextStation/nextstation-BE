package com.cotato.nextstation.domain.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// kapi 에러 본문, kauth의 KakaoErrorResponse(error/error_code)와 형식이 다르다
// 예: {"msg":"this user is not signed up","code":-101}
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoApiErrorResponse(
        String msg,
        Integer code
) {
}
