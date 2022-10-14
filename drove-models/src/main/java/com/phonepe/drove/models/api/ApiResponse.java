package com.phonepe.drove.models.api;

import lombok.Value;

/**
 *
 */
@Value
public class ApiResponse<T> {
    ApiErrorCode status;
    T data;
    String message;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ApiErrorCode.SUCCESS, data, "success");
    }

    public static <T> ApiResponse<T> failure(final String message) {
        return failure(null, message);
    }

    public static <T> ApiResponse<T> failure(final T data, final String message) {
        return new ApiResponse<>(ApiErrorCode.FAILED, data, message);
    }
}
