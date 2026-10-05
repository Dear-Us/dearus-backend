package com.dearusback.care.domain.exception;

import com.dearusback.support.domain.exception.ErrorCode;
import com.dearusback.support.domain.exception.ErrorType;

public enum CareErrorCode implements ErrorCode {
    INVALID_RECORD("INVALID_RECORD", "육아 기록 정보가 올바르지 않습니다.", ErrorType.BAD_REQUEST);

    private final String code;
    private final String message;
    private final ErrorType type;

    CareErrorCode(String code, String message, ErrorType type) {
        this.code = code;
        this.message = message;
        this.type = type;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public ErrorType type() { return type; }
}
