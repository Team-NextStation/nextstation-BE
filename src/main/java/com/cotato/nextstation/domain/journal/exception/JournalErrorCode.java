package com.cotato.nextstation.domain.journal.exception;

import com.cotato.nextstation.global.exception.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum JournalErrorCode implements ErrorCode {

    INVALID_JOURNAL_PHOTO(HttpStatus.BAD_REQUEST, "CLIENT_ERROR_400_INVALID_JOURNAL_PHOTO", "UPDATE 액션에는 이미지 URL이 필요합니다."),

    JOURNAL_FORBIDDEN(HttpStatus.FORBIDDEN, "CLIENT_ERROR_403_JOURNAL_FORBIDDEN", "본인 여행일지만 수정/삭제할 수 있습니다."),
    JOURNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "CLIENT_ERROR_404_JOURNAL_NOT_FOUND", "존재하지 않는 여행일지입니다."),
    MEMBER_STAMP_NOT_FOUND(HttpStatus.NOT_FOUND, "CLIENT_ERROR_404_MEMBER_STAMP_NOT_FOUND", "존재하지 않는 스탬프입니다."),
    // CourseErrorCode.COURSE_NOT_FOUND와 코드·메시지를 동일하게 맞춘 로컬 상수.
    // JournalQueryService가 코스 스냅샷 조회(course 테이블 직접 조회)에 실패했을 때 쓴다 —
    // Course 도메인 클래스에 의존하지 않기 위해 MEMBER_STAMP_NOT_FOUND와 같은 방식으로 따로 둔다.
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "CLIENT_ERROR_404_COURSE_NOT_FOUND", "존재하지 않는 코스입니다."),
    JOURNAL_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CLIENT_ERROR_404_JOURNAL_IMAGE_NOT_FOUND", "해당 여행일지에 존재하지 않는 사진입니다."),

    JOURNAL_ALREADY_EXISTS(HttpStatus.CONFLICT, "CLIENT_ERROR_409_JOURNAL_ALREADY_EXISTS", "해당 스탬프로 이미 작성된 여행일지가 있습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}