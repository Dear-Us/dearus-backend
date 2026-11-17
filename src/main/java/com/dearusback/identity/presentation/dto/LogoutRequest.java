package com.dearusback.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 세션의 Refresh Token만 폐기")
public record LogoutRequest(@NotBlank @Schema(description = "폐기할 현재 기기의 Refresh Token",
        example = "opaque-refresh-token", requiredMode = Schema.RequiredMode.REQUIRED) String refreshToken) {
}
