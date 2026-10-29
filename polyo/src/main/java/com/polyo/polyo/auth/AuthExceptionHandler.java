package com.polyo.polyo.auth;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Must outrank GlobalExceptionHandler, whose Exception handler would otherwise turn AuthException into 500.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage()).orElse("요청 형식이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(ApiResponse.failure("INVALID_REQUEST", message));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handle(AuthException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.failure(exception.code(), exception.getMessage()));
    }
}
