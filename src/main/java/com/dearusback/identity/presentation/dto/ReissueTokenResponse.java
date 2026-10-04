package com.dearusback.identity.presentation.dto;

import com.dearusback.identity.application.model.ReissueTokenResult;

public record ReissueTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresInSeconds,
        long refreshTokenExpiresInSeconds
) {

    public static ReissueTokenResponse from(ReissueTokenResult result) {
        return new ReissueTokenResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.accessTokenExpiresInSeconds(),
                result.refreshTokenExpiresInSeconds()
        );
    }
}
