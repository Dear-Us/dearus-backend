package com.dearusback.voice.domain.exception;

import com.dearusback.support.domain.exception.ErrorCode;
import com.dearusback.support.domain.exception.ErrorType;

public enum VoiceErrorCode implements ErrorCode {
    INVALID_AUDIO("INVALID_AUDIO", "음성 파일이 올바르지 않습니다.", ErrorType.BAD_REQUEST),
    AUDIO_TOO_LARGE("AUDIO_TOO_LARGE", "음성 파일 크기를 초과했습니다.", ErrorType.PAYLOAD_TOO_LARGE),
    INVALID_REQUEST("INVALID_VOICE_REQUEST", "음성 요청 정보가 올바르지 않습니다.", ErrorType.BAD_REQUEST),
    REQUEST_NOT_FOUND("VOICE_REQUEST_NOT_FOUND", "음성 요청을 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    REQUEST_CONFLICT("VOICE_REQUEST_CONFLICT", "음성 요청 상태가 충돌합니다.", ErrorType.CONFLICT),
    VOICE_UNAVAILABLE("VOICE_UNAVAILABLE", "음성 기능을 사용할 수 없습니다.", ErrorType.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final ErrorType type;

    VoiceErrorCode(String code, String message, ErrorType type) {
        this.code = code; this.message = message; this.type = type;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public ErrorType type() { return type; }
}
