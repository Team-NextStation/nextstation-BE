package com.cotato.nextstation.global.exception;

import com.cotato.nextstation.global.common.response.CommonResponse;
import com.cotato.nextstation.global.exception.error.GlobalErrorCode;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 검증 에러 (MethodArgumentNotValidException) -> 400 Bad Request 반환
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, Object> reasons = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                reasons.put(error.getField(), error.getDefaultMessage())
        );
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                reasons.put(error.getObjectName(), error.getDefaultMessage())
        );

        CommonResponse<Void> response = CommonResponse.error(GlobalErrorCode.VALIDATION_ERROR, reasons);
        return ResponseEntity.status(GlobalErrorCode.VALIDATION_ERROR.getHttpStatus()).body(response);
    }

    /**
     * 쿼리 파라미터·경로 변수 검증 에러 (ConstraintViolationException) -> 400 Bad Request 반환
     * <p>
     * {@code @Validated}가 붙은 컨트롤러의 메서드 파라미터 제약이 깨졌을 때 발생한다.
     * 요청 본문 검증과 달리 MethodArgumentNotValidException이 아니라 이 예외가 오므로 따로 받는다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, Object> reasons = new HashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                reasons.put(extractFieldName(violation.getPropertyPath()), violation.getMessage())
        );

        CommonResponse<Void> response = CommonResponse.error(GlobalErrorCode.VALIDATION_ERROR, reasons);
        return ResponseEntity.status(GlobalErrorCode.VALIDATION_ERROR.getHttpStatus()).body(response);
    }

    /**
     * 파라미터 검증 실패의 경로는 "메서드명.파라미터명" 형태다.
     * 마지막 마디만 남겨 요청 본문 검증(필드명만 담는다)과 reasons의 키 모양을 맞춘다.
     */
    private String extractFieldName(Path propertyPath) {
        String path = propertyPath.toString();
        int lastSeparator = path.lastIndexOf('.');
        return (lastSeparator < 0) ? path : path.substring(lastSeparator + 1);
    }

    /**
     * 커스텀 예외 -> 에러 코드에 매핑된 HTTP 상태 코드 반환
     * 5xx는 서버 결함이므로 ERROR로 남겨 운영 알림(디스코드) 대상이 되게 한다.
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CommonResponse<Void>> handleCustomException(CustomException ex) {
        HttpStatus status = ex.getErrorCode().getHttpStatus();
        if (status.is5xxServerError()) {
            log.error("CustomException: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage(), ex);
        } else {
            log.warn("CustomException: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
        }

        CommonResponse<Void> response = CommonResponse.error(ex.getErrorCode());
        return ResponseEntity.status(status).body(response);
    }

    /**
     * 요청 본문이 비어있거나 JSON 형식이 잘못된 경우 -> 400 Bad Request 반환
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("HttpMessageNotReadableException: {}", ex.getMessage());

        CommonResponse<Void> response = CommonResponse.error(GlobalErrorCode.INVALID_REQUEST);
        return ResponseEntity.status(GlobalErrorCode.INVALID_REQUEST.getHttpStatus()).body(response);
    }

    /**
     * 필수 요청 파라미터가 누락된 경우 -> 400 Bad Request 반환
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.warn("MissingServletRequestParameterException: {}", ex.getMessage());

        CommonResponse<Void> response = CommonResponse.error(GlobalErrorCode.INVALID_REQUEST);
        return ResponseEntity.status(GlobalErrorCode.INVALID_REQUEST.getHttpStatus()).body(response);
    }

    /**
     * 요청 파라미터의 타입이 일치하지 않는 경우 -> 400 Bad Request 반환
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.warn("MethodArgumentTypeMismatchException: {}", ex.getMessage());

        CommonResponse<Void> response = CommonResponse.error(GlobalErrorCode.INVALID_REQUEST);
        return ResponseEntity.status(GlobalErrorCode.INVALID_REQUEST.getHttpStatus()).body(response);
    }

    /**
     * 지원하지 않는 HTTP 메서드로 요청한 경우 -> 405 Method Not Allowed 반환
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResponse<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.warn("HttpRequestMethodNotSupportedException: {}", ex.getMessage());

        CommonResponse<Void> response = CommonResponse.error(GlobalErrorCode.METHOD_NOT_ALLOWED);
        return ResponseEntity.status(GlobalErrorCode.METHOD_NOT_ALLOWED.getHttpStatus()).body(response);
    }

    /**
     * 존재하지 않는 경로로 요청한 경우 -> 404 Not Found 반환
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("NoResourceFoundException: {}", ex.getMessage());

        CommonResponse<Void> response = CommonResponse.error(GlobalErrorCode.NOT_FOUND);
        return ResponseEntity.status(GlobalErrorCode.NOT_FOUND.getHttpStatus()).body(response);
    }

    /**
     * 기타 모든 예외 -> 500 Internal Server Error 반환
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Unexpected error: ", ex);

        CommonResponse<Void> response = CommonResponse.error(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}