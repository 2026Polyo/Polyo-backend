package com.polyo.polyo.matchroom;

import com.polyo.polyo.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MatchRoomController.class)
public class MatchRoomExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(MatchRoomExceptionHandler.class);

    @ExceptionHandler(MatchRoomException.class)
    public ResponseEntity<ApiResponse<Void>> handleRoom(MatchRoomException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.failure(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("INVALID_REQUEST", "요청 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unexpected match room failure", exception);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.failure("INTERNAL_SERVER_ERROR", "일시적인 서버 오류가 발생했습니다."));
    }
}
