package com.polyo.polyo.common.api;

public record ApiSuccess<T>(boolean success, T data) {
    public static <T> ApiSuccess<T> of(T data) {
        return new ApiSuccess<>(true, data);
    }
}
