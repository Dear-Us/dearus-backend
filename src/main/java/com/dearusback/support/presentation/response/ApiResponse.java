package com.dearusback.support.presentation.response;

import com.dearusback.support.domain.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> successWithoutData() {
        return success(null);
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode) {
        ErrorCode requiredErrorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        return new ApiResponse<>(false, null, requiredErrorCode.message(), requiredErrorCode.code());
    }
}
