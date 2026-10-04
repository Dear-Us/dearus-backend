package com.dearusback.identity.application.model;

public record ReissueTokenCommand(String refreshToken) {

    public ReissueTokenCommand {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }
    }
}
