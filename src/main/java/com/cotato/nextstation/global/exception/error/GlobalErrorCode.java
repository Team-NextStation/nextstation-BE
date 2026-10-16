package com.cotato.nextstation.global.exception.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

    // 400 Bad Request
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "CLIENT_ERROR_400_INVALID_REQUEST", "유효하지 않은 요청입니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "CLIENT_ERROR_400_VALIDATION_ERROR", "요청 값이 유효하지 않습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "CLIENT_ERROR_400_INVALID_CURSOR", "유효하지 않은 커서입니다."),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "CLIENT_ERROR_400_INVALID_PAGE_SIZE", "size는 1 이상 50 이하여야 합니다."),
    CONTAINS_BANNED_WORD(HttpStatus.BAD_REQUEST, "CLIENT_ERROR_400_CONTAINS_BANNED_WORD", "금칙어가 포함되어 있습니다."),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "CLIENT_ERROR_401_UNAUTHORIZED", "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "CLIENT_ERROR_401_INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "CLIENT_ERROR_401_EXPIRED_TOKEN", "만료된 토큰입니다."),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN, "CLIENT_ERROR_403_FORBIDDEN", "접근 권한이 없습니다."),

    // 404 Not Found
    NOT_FOUND(HttpStatus.NOT_FOUND, "CLIENT_ERROR_404_NOT_FOUND", "리소스를 찾을 수 없습니다."),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "CLIENT_ERROR_405_METHOD_NOT_ALLOWED", "허용되지 않은 HTTP 메서드입니다."),

    // 409 Conflict
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "CLIENT_ERROR_409_DUPLICATE_RESOURCE", "이미 존재하는 리소스입니다."),

    // 429 Too Many Requests
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "CLIENT_ERROR_429_TOO_MANY_REQUESTS", "요청 횟수 제한을 초과했습니다."),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR_500_INTERNAL_SERVER_ERROR", "서버 내부 오류입니다."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR_500_DATABASE_ERROR", "데이터베이스 오류입니다."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "SERVER_ERROR_502_EXTERNAL_API_ERROR", "외부 API 통신 오류입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
