package com.cotato.nextstation.domain.block.exception;

import com.cotato.nextstation.global.exception.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberBlockErrorCode implements ErrorCode {

    SELF_BLOCK_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CLIENT_ERROR_400_SELF_BLOCK_NOT_ALLOWED", "자기 자신은 차단할 수 없습니다."),
    ALREADY_BLOCKED(HttpStatus.CONFLICT, "CLIENT_ERROR_409_ALREADY_BLOCKED", "이미 차단한 사용자입니다."),
    BLOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "CLIENT_ERROR_404_BLOCK_NOT_FOUND", "차단하지 않은 사용자입니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
