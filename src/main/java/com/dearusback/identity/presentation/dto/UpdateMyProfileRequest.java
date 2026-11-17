package com.dearusback.identity.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "multipart의 application/json request part. 생략한 필드는 유지. 적어도 하나의 변경 필요")
public record UpdateMyProfileRequest(
        @Schema(description = "생략 시 유지. 공백 제거 후 1~50자; null은 변경 없음", example = "서은",
                minLength = 1, maxLength = 50) String memberName,
        @Schema(description = "true면 사진 제거. profileImage 파일 part와 동시에 사용할 수 없음", example = "false")
        Boolean removeProfileImage) {

    public boolean shouldRemoveProfileImage() {
        return Boolean.TRUE.equals(removeProfileImage);
    }
}
