package com.dearusback.family.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "multipart의 필수 application/json request part. 이미지 파일은 선택적 profileImage part")
public record CreateBabyRequest(
        @Schema(description = "공백 제거 후 1~50자", example = "하늘", minLength = 1, maxLength = 50,
                requiredMode = Schema.RequiredMode.REQUIRED) String babyName,
        @Schema(description = "생년월일 YYYY-MM-DD. 회원 timezone 기준 미래 날짜 불가", example = "2026-01-01",
                pattern = "[0-9]{4}-[0-9]{2}-[0-9]{2}", requiredMode = Schema.RequiredMode.REQUIRED) String birthDate,
        @Schema(description = "MALE 남아 / FEMALE 여아", example = "FEMALE", allowableValues = {"MALE", "FEMALE"},
                requiredMode = Schema.RequiredMode.REQUIRED) String gender) {
}
