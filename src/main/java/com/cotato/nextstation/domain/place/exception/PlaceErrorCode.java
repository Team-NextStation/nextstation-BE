package com.cotato.nextstation.domain.place.exception;

import com.cotato.nextstation.global.exception.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PlaceErrorCode implements ErrorCode {
    INVALID_PLACE_REVIEW_IMAGE(HttpStatus.BAD_REQUEST, "CLIENT_ERROR_400_INVALID_PLACE_REVIEW_IMAGE", "UPDATE 액션에는 이미지 URL이 필요합니다."),
    PLACE_REVIEW_NOT_FOUND(HttpStatus.BAD_REQUEST, "CLIENT_ERROR_400_PLACE_REVIEW_NOT_FOUND", "해당 여행일지에 존재하지 않는 장소의 리뷰는 수정할 수 없습니다"),
    PLACE_NOT_IN_COURSE(HttpStatus.BAD_REQUEST, "CLIENT_ERROR_400_PLACE_NOT_IN_COURSE", "해당 코스에 포함되지 않은 장소는 리뷰를 작성할 수 없습니다."),

    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "CLIENT_ERROR_404_PLACE_NOT_FOUND", "존재하지 않는 장소입니다."),

    PLACE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "CLIENT_ERROR_409_PLACE_ALREADY_REGISTERED", "해당 역에 이미 등록된 장소입니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}