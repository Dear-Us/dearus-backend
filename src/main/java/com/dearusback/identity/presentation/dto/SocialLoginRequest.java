package com.dearusback.identity.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(
        @NotBlank String provider,
        @NotBlank String providerToken
) {
}
