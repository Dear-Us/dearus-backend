package com.dearusback.identity.application.model;

public record SocialLoginResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresInSeconds,
        long refreshTokenExpiresInSeconds,
        boolean newMember,
        MemberResult member
) {

    public static final String BEARER = "Bearer";

    public record MemberResult(
            Long memberId,
            String memberName,
            String profileImageUrl,
            Long lastSelectedBabyId
    ) {
    }
}
