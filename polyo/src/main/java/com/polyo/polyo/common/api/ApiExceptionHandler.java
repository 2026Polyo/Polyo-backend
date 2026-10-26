package com.polyo.polyo.common.api;

import static com.polyo.polyo.common.error.CommonErrorCode.*;

import com.polyo.polyo.common.error.ApiException;
import com.polyo.polyo.common.error.ErrorCode;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/** MVC 공통 오류 처리. Security 필터 오류는 인증 설정에서 별도로 연결한다. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiFailure> applicationError(ApiException exception) {
        return failure(exception.errorCode());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        ConstraintViolationException.class,
        HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ApiFailure> invalidBody(Exception exception) {
        return failure(INVALID_REQUEST_BODY);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiFailure> invalidParameter(Exception exception) {
        return failure(INVALID_REQUEST_PARAM);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiFailure> unsupportedMethod(
            HttpRequestMethodNotSupportedException exception) {
        var response = ResponseEntity.status(METHOD_NOT_ALLOWED.status());
        var supportedMethods = exception.getSupportedHttpMethods();
        if (supportedMethods != null) {
            String allowed =
                    supportedMethods.stream()
                            .map(HttpMethod::name)
                            .collect(Collectors.joining(", "));
            response.header(HttpHeaders.ALLOW, allowed);
        }
        return response.body(ApiFailure.of(METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiFailure> notFound(Exception exception) {
        return failure(API_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiFailure> unexpected(Exception exception) {
        // 원문 메시지에는 인증 정보나 외부 서비스 응답이 포함될 수 있다.
        log.error("Unhandled API exception type={}", exception.getClass().getName());
        return failure(INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiFailure> failure(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status()).body(ApiFailure.of(errorCode));
    }
}
