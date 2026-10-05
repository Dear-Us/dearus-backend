package com.dearusback.care.domain.exception;

import com.dearusback.support.domain.exception.ErrorCode;
import com.dearusback.support.domain.exception.ErrorType;

public enum CareErrorCode implements ErrorCode {
    INVALID_RECORD("INVALID_RECORD", "육아 기록 정보가 올바르지 않습니다.", ErrorType.BAD_REQUEST),
    INVALID_RECORD_QUERY("INVALID_RECORD_QUERY", "육아 기록 조회 조건이 올바르지 않습니다.", ErrorType.BAD_REQUEST),
    RECORD_NOT_FOUND("RECORD_NOT_FOUND", "육아 기록을 찾을 수 없습니다.", ErrorType.NOT_FOUND);

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
