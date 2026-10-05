package com.dearusback.growth.domain.exception;

import com.dearusback.support.domain.exception.ErrorCode;
import com.dearusback.support.domain.exception.ErrorType;

public enum GrowthErrorCode implements ErrorCode {
    INVALID_GROWTH_RECORD("INVALID_GROWTH_RECORD", "성장 기록 정보가 올바르지 않습니다.", ErrorType.BAD_REQUEST),
    GROWTH_RECORD_NOT_FOUND("GROWTH_RECORD_NOT_FOUND", "성장 기록을 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    GROWTH_RECORD_VERSION_CONFLICT("GROWTH_RECORD_VERSION_CONFLICT", "기록이 다른 요청에 의해 변경되었습니다.",
            ErrorType.CONFLICT);

    private final String code;
    private final String message;
    private final ErrorType type;

    GrowthErrorCode(String code, String message, ErrorType type) {
        this.code = code;
        this.message = message;
        this.type = type;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public ErrorType type() { return type; }
}
