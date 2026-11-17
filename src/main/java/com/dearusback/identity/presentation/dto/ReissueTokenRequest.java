package com.dearusback.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 기기의 Refresh Token. 성공 시 원문 토큰은 한 번만 회전되므로 새 토큰으로 교체")
public record ReissueTokenRequest(@NotBlank @Schema(description = "로그인 또는 직전 재발급에서 받은 불투명 Refresh Token",
        example = "opaque-refresh-token", requiredMode = Schema.RequiredMode.REQUIRED) String refreshToken) {
}
