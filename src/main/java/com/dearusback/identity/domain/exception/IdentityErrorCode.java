package com.dearusback.identity.domain.exception;

import com.dearusback.support.domain.exception.ErrorCode;
import com.dearusback.support.domain.exception.ErrorType;

public enum IdentityErrorCode implements ErrorCode {
    UNSUPPORTED_SOCIAL_PROVIDER(
            "UNSUPPORTED_SOCIAL_PROVIDER",
            "지원하지 않는 소셜 로그인 제공자입니다.",
            ErrorType.BAD_REQUEST
    ),
    INVALID_SOCIAL_TOKEN(
            "INVALID_SOCIAL_TOKEN",
            "소셜 인증 정보가 유효하지 않습니다.",
            ErrorType.UNAUTHORIZED
    ),
    SOCIAL_PROVIDER_UNAVAILABLE(
            "SOCIAL_PROVIDER_UNAVAILABLE",
            "소셜 로그인 제공자에 일시적으로 연결할 수 없습니다.",
            ErrorType.SERVICE_UNAVAILABLE
    ),
    WITHDRAWN_MEMBER(
            "WITHDRAWN_MEMBER",
            "탈퇴한 회원은 로그인할 수 없습니다.",
            ErrorType.FORBIDDEN
    ),
    SOCIAL_ACCOUNT_CONFLICT(
            "SOCIAL_ACCOUNT_CONFLICT",
            "소셜 계정 처리 중 충돌이 발생했습니다.",
            ErrorType.CONFLICT
    ),
    INVALID_REFRESH_TOKEN(
            "INVALID_REFRESH_TOKEN",
            "인증 정보가 유효하지 않습니다.",
            ErrorType.UNAUTHORIZED
    );

    private final String code;
    private final String message;
    private final ErrorType type;

    IdentityErrorCode(String code, String message, ErrorType type) {
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
