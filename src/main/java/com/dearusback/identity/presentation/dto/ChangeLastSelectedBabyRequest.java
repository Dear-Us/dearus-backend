package com.dearusback.identity.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 ACTIVE 공동 보호자인 아기를 마지막 선택 대상으로 지정")
public record ChangeLastSelectedBabyRequest(@NotNull @Positive @Schema(description = "양의 Baby ID",
        example = "1", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long babyId) {
}
