package com.polyo.polyo.credit.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Gateway 연결 전 CREDIT 요청 추적. 인증 기능을 대체하지 않는다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CreditRequestLoggingFilter extends OncePerRequestFilter {
    public static final String TRACE_ATTRIBUTE = "credit.traceId";
    public static final String USER_ATTRIBUTE = "credit.authenticatedUserId";

    private static final String TRACE_HEADER = "Trace-Id";
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Logger log = LoggerFactory.getLogger(CreditRequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/credit/v11/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_HEADER));
        request.setAttribute(TRACE_ATTRIBUTE, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            logRequest(request, response, traceId, startedAt);
        }
    }

    private String resolveTraceId(String supplied) {
        if (supplied != null && VALID_TRACE_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    private void logRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            String traceId,
            long startedAt) {
        Object user = request.getAttribute(USER_ATTRIBUTE);
        String userId = user instanceof String id ? sanitize(id) : "anonymous";
        long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        log.info(
                "traceId={} userId={} serviceName=credit method={} path={} status={} elapsedTime={}",
                traceId,
                userId,
                request.getMethod(),
                sanitize(request.getRequestURI()),
                response.getStatus(),
                elapsedTime);
    }

    private String sanitize(String value) {
        return value.replaceAll("[\r\n\t]", "_");
    }
}
