package com.dearusback.support.application.exception;

import com.dearusback.support.domain.exception.ErrorCode;
import com.dearusback.support.domain.exception.ErrorType;

public enum CommonErrorCode implements ErrorCode {
    INVALID_REQUEST("INVALID_REQUEST", "잘못된 요청입니다.", ErrorType.BAD_REQUEST),
    PAYLOAD_TOO_LARGE("PAYLOAD_TOO_LARGE", "요청 파일 크기가 허용 범위를 초과했습니다.", ErrorType.PAYLOAD_TOO_LARGE),
    INTERNAL_SERVER_ERROR(
            "INTERNAL_SERVER_ERROR",
            "서버 내부 오류가 발생했습니다.",
            ErrorType.INTERNAL_SERVER_ERROR
    );

    private final String code;
    private final String message;
    private final ErrorType type;

    CommonErrorCode(String code, String message, ErrorType type) {
        this.code = code;
        this.message = message;
        this.type = type;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public ErrorType type() {
        return type;
    }
}
