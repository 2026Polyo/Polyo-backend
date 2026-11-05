package com.polyo.polyo.credit.web;

import com.polyo.polyo.common.GlobalExceptionHandler;
import com.polyo.polyo.common.api.ApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 컨트롤러가 결정되기 전의 CREDIT 라우팅 오류를 처리한다. 다른 도메인은 기존 공통 처리기에 위임하여 응답 계약을 유지한다. */
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@RestControllerAdvice
public class CreditRoutingExceptionHandler {
  private final GlobalExceptionHandler fallback;
  private final ApiExceptionHandler credit = new ApiExceptionHandler();

  public CreditRoutingExceptionHandler(GlobalExceptionHandler fallback) {
    this.fallback = fallback;
  }

  @ExceptionHandler({
    HttpRequestMethodNotSupportedException.class,
    HttpMediaTypeNotSupportedException.class,
    NoHandlerFoundException.class,
    NoResourceFoundException.class
  })
  public ResponseEntity<?> handleRouting(Exception exception, HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (!path.startsWith("/api/credit/v11/")) {
      return fallback.handleUnexpected(exception);
    }
    if (exception instanceof HttpRequestMethodNotSupportedException methodError) {
      return credit.unsupportedMethod(methodError);
    }
    if (exception instanceof HttpMediaTypeNotSupportedException) {
      return credit.invalidBody(exception);
    }
    return credit.notFound(exception);
  }
}
