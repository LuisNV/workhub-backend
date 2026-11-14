package com.thanh.workhub.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Lỗi nghiệp vụ đi qua {@link ApiException}. Lỗi của Spring MVC (validation, 405, sai kiểu tham số...)
 * do {@link ResponseEntityExceptionHandler} tạo ProblemDetail, sau đó {@link ApiResponseAdvice} bọc lại.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.error(
                exception.getCode(), exception.getStatus().value(), exception.getMessage(),
                TraceIdFilter.traceId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Lỗi xử lý request", exception);
        return ProblemDetail.forStatusAndDetail(ErrorCode.INTERNAL_ERROR.status(),
                ErrorCode.INTERNAL_ERROR.defaultMessage());
    }
}
