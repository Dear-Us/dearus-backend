package com.dearusback.support.presentation.response;

import com.dearusback.support.domain.exception.ErrorCode;
import java.util.Objects;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> successWithoutData() {
        return success(null);
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode) {
        ErrorCode requiredErrorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        return new ApiResponse<>(false, null, requiredErrorCode.message());
    }
}
