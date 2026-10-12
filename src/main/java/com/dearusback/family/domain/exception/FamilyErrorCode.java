package com.dearusback.family.domain.exception;

import com.dearusback.support.domain.exception.ErrorCode;
import com.dearusback.support.domain.exception.ErrorType;

public enum FamilyErrorCode implements ErrorCode {
    INVALID_BABY_ID("INVALID_BABY_ID", "아기 ID가 올바르지 않습니다.", ErrorType.BAD_REQUEST),
    BABY_NOT_FOUND("BABY_NOT_FOUND", "아기 정보를 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    BABY_UPDATE_FORBIDDEN("BABY_UPDATE_FORBIDDEN", "아기 정보를 수정할 권한이 없습니다.", ErrorType.FORBIDDEN),
    BABY_DELETE_FORBIDDEN("BABY_DELETE_FORBIDDEN", "아기를 삭제할 권한이 없습니다.", ErrorType.FORBIDDEN),
    BABY_INVITATION_FORBIDDEN("BABY_INVITATION_FORBIDDEN", "공동 보호자를 초대할 권한이 없습니다.", ErrorType.FORBIDDEN),
    INVALID_INVITATION_ID("INVALID_INVITATION_ID", "초대 ID가 올바르지 않습니다.", ErrorType.BAD_REQUEST),
    INVITATION_NOT_FOUND("INVITATION_NOT_FOUND", "초대 정보를 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    INVITATION_CONFLICT("INVITATION_CONFLICT", "취소할 수 없는 초대입니다.", ErrorType.CONFLICT),
    INVITATION_ACCEPT_CONFLICT("INVITATION_ACCEPT_CONFLICT", "수락할 수 없는 초대입니다.", ErrorType.CONFLICT),
    INVALID_MEMBER_ID("INVALID_MEMBER_ID", "회원 ID가 올바르지 않습니다.", ErrorType.BAD_REQUEST),
    BABY_MEMBER_NOT_FOUND("BABY_MEMBER_NOT_FOUND", "공동 보호자 정보를 찾을 수 없습니다.", ErrorType.NOT_FOUND),
    BABY_MEMBER_MANAGEMENT_FORBIDDEN("BABY_MEMBER_MANAGEMENT_FORBIDDEN", "공동 보호자를 관리할 권한이 없습니다.", ErrorType.FORBIDDEN),
    BABY_MEMBER_OWNER_CONFLICT("BABY_MEMBER_OWNER_CONFLICT", "OWNER 보호자 관계는 종료할 수 없습니다.", ErrorType.CONFLICT),
    BABY_OWNER_TRANSFER_CONFLICT("BABY_OWNER_TRANSFER_CONFLICT", "OWNER에게 권한을 이전할 수 없습니다.", ErrorType.CONFLICT),
    INVALID_BABY_UPDATE("INVALID_BABY_UPDATE", "수정할 아기 정보가 올바르지 않습니다.", ErrorType.BAD_REQUEST),
    INVALID_BABY_NAME("INVALID_BABY_NAME", "아기 이름은 1~50자여야 합니다.", ErrorType.BAD_REQUEST),
    INVALID_BIRTH_DATE("INVALID_BIRTH_DATE", "생년월일이 올바르지 않습니다.", ErrorType.BAD_REQUEST),
    INVALID_GENDER("INVALID_GENDER", "성별이 올바르지 않습니다.", ErrorType.BAD_REQUEST),
    INVALID_BABY_IMAGE("INVALID_BABY_IMAGE", "지원하지 않는 아기 프로필 이미지입니다.", ErrorType.BAD_REQUEST),
    BABY_IMAGE_TOO_LARGE("BABY_IMAGE_TOO_LARGE", "아기 프로필 이미지는 5MB 이하여야 합니다.", ErrorType.PAYLOAD_TOO_LARGE),
    BABY_IMAGE_STORAGE_UNAVAILABLE("BABY_IMAGE_STORAGE_UNAVAILABLE", "아기 프로필 이미지를 처리할 수 없습니다.", ErrorType.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final ErrorType type;

    FamilyErrorCode(String code, String message, ErrorType type) {
        this.code = code;
        this.message = message;
        this.type = type;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public ErrorType type() { return type; }
}
