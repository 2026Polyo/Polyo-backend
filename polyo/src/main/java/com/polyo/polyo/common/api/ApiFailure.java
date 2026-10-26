package com.polyo.polyo.common.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.polyo.polyo.common.error.ErrorCode;

import java.time.Instant;

public record ApiFailure(
        boolean success,
        ErrorDetail error,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp) {

    public record ErrorDetail(String code, String message, int status) {}

    public static ApiFailure of(ErrorCode errorCode) {
        var detail = new ErrorDetail(errorCode.code(), errorCode.message(), errorCode.status());
        return new ApiFailure(false, detail, Instant.now());
    }
}
