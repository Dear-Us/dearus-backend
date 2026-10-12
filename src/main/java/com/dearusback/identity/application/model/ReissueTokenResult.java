package com.dearusback.identity.application.model;

public record ReissueTokenResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresInSeconds,
        long refreshTokenExpiresInSeconds
) {
}
