package com.cotato.nextstation.domain.moderation.exception;

import com.cotato.nextstation.global.exception.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

    REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "CLIENT_ERROR_409_REPORT_ALREADY_EXISTS", "이미 신고한 콘텐츠입니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
