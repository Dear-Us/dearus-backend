package com.dearusback.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ReissueTokenRequest(@NotBlank String refreshToken) {
}
