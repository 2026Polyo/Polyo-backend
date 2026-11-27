package com.polyo.polyo.common.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** One response envelope shared by every HTTP API. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetail error,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp) {

    public record ErrorDetail(String code, String message, Integer status) {}

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> failure(String code, String message, int status) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, message, status), Instant.now());
    }

    public static <T> ApiResponse<T> failure(com.polyo.polyo.common.error.ErrorCode errorCode) {
        return failure(errorCode.code(), errorCode.message(), errorCode.status());
    }
}
