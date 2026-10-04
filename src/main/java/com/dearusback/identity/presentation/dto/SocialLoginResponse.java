package com.dearusback.identity.presentation.dto;

import com.dearusback.identity.application.model.SocialLoginResult;

public record SocialLoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresInSeconds,
        long refreshTokenExpiresInSeconds,
        boolean isNewMember,
        MemberResponse member
) {

    public static SocialLoginResponse from(SocialLoginResult result) {
        return new SocialLoginResponse(
                result.accessToken(),
                result.refreshToken(),
                result.tokenType(),
                result.accessTokenExpiresInSeconds(),
                result.refreshTokenExpiresInSeconds(),
                result.newMember(),
                new MemberResponse(
                        result.member().memberId(),
                        result.member().memberName(),
                        result.member().profileImageUrl(),
                        result.member().lastSelectedBabyId()
                )
        );
    }

    public record MemberResponse(
            Long memberId,
            String memberName,
            String profileImageUrl,
            Long lastSelectedBabyId
    ) {
    }
}
