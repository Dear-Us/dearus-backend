package com.dearusback.family.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "multipart의 application/json request part 또는 JSON 요청 본문. 생략은 기존 값 유지. 변경 필드 최소 하나 필요")
public record UpdateBabyRequest(
        @Schema(description = "생략 시 유지. 공백 제거 후 1~50자", example = "하늘", minLength = 1,
                maxLength = 50) String babyName,
        @Schema(description = "생략 시 유지. YYYY-MM-DD, 회원 timezone 기준 미래 날짜 불가", example = "2026-01-01",
                pattern = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String birthDate,
        @Schema(description = "생략 시 유지. MALE 남아 / FEMALE 여아", example = "FEMALE",
                allowableValues = {"MALE", "FEMALE"}) String gender,
        @Schema(description = "true면 사진 제거. profileImage 파일 part와 동시 사용 불가", example = "false")
        Boolean removeProfileImage,
        @Schema(description = "외부 HTTPS 이미지 URL. profileImage 파일 또는 removeProfileImage와 동시 사용 불가", example = "https://cdn.example.com/baby.png")
        String profileImageUrl) {
    public boolean shouldRemoveProfileImage() {
        return Boolean.TRUE.equals(removeProfileImage);
    }
}
