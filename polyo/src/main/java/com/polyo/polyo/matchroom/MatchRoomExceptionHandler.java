package com.polyo.polyo.matchroom;

import com.polyo.polyo.common.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Must outrank GlobalExceptionHandler, whose Exception handler would otherwise turn these into 500s.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MatchRoomController.class)
public class MatchRoomExceptionHandler {
    @ExceptionHandler(MatchRoomException.class)
    public ResponseEntity<ApiResponse<Void>> handleRoom(MatchRoomException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.failure(exception.code(), exception.getMessage()));
    }
}
