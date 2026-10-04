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
            "탈퇴한 회원입니다.",
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
    ),
    INVALID_AUTHENTICATION(
            "INVALID_AUTHENTICATION",
            "인증 정보가 유효하지 않습니다.",
            ErrorType.UNAUTHORIZED
    ),
    ACCESSIBLE_BABY_NOT_FOUND(
            "ACCESSIBLE_BABY_NOT_FOUND",
            "아기 정보를 찾을 수 없습니다.",
            ErrorType.NOT_FOUND
    ),
    INVALID_LAST_SELECTED_BABY_ID(
            "INVALID_LAST_SELECTED_BABY_ID",
            "아기 ID가 올바르지 않습니다.",
            ErrorType.BAD_REQUEST
    ),
    INVALID_PROFILE_UPDATE(
            "INVALID_PROFILE_UPDATE",
            "수정할 회원 정보가 올바르지 않습니다.",
            ErrorType.BAD_REQUEST
    ),
    INVALID_MEMBER_NAME(
            "INVALID_MEMBER_NAME",
            "회원 이름은 1~50자여야 합니다.",
            ErrorType.BAD_REQUEST
    ),
    INVALID_PROFILE_IMAGE(
            "INVALID_PROFILE_IMAGE",
            "지원하지 않는 프로필 이미지입니다.",
            ErrorType.BAD_REQUEST
    ),
    PROFILE_IMAGE_TOO_LARGE(
            "PROFILE_IMAGE_TOO_LARGE",
            "프로필 이미지는 5MB 이하여야 합니다.",
            ErrorType.PAYLOAD_TOO_LARGE
    ),
    PROFILE_IMAGE_STORAGE_UNAVAILABLE(
            "PROFILE_IMAGE_STORAGE_UNAVAILABLE",
            "프로필 이미지를 처리할 수 없습니다.",
            ErrorType.SERVICE_UNAVAILABLE
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
