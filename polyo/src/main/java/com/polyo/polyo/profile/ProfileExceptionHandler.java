package com.polyo.polyo.profile;

import com.polyo.polyo.common.api.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ProfileController.class)
public class ProfileExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getDefaultMessage()).orElse("프로필 형식이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(ApiResponse.failure("INVALID_PROFILE", message, 400));
    }

    @ExceptionHandler(ProfileException.class)
    public ResponseEntity<ApiResponse<Void>> profile(ProfileException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.failure(exception.code(), exception.getMessage(), exception.status()));
    }
}
