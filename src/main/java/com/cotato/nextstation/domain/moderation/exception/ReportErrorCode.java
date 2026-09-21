package com.cotato.nextstation.domain.moderation.exception;

import com.cotato.nextstation.global.exception.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

    SELF_REPORT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CLIENT_ERROR_400_SELF_REPORT_NOT_ALLOWED", "본인이 작성한 콘텐츠는 신고할 수 없습니다."),

    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "CLIENT_ERROR_404_REPORT_TARGET_NOT_FOUND", "존재하지 않는 신고 대상입니다."),

    REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "CLIENT_ERROR_409_REPORT_ALREADY_EXISTS", "이미 신고한 콘텐츠입니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
