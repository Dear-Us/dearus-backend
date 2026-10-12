package com.dearusback.identity.presentation.dto;

import com.dearusback.identity.application.model.MyProfileResult;
import com.dearusback.identity.domain.type.SocialProvider;
import java.util.List;

public record MyProfileResponse(
        Long memberId,
        String email,
        String memberName,
        String profileImageUrl,
        String timezone,
        Long lastSelectedBabyId,
        List<SocialAccountResponse> socialAccounts
) {

    public static MyProfileResponse from(MyProfileResult result) {
        return new MyProfileResponse(
                result.memberId(),
                result.email(),
                result.memberName(),
                result.profileImageUrl(),
                result.timezone(),
                result.lastSelectedBabyId(),
                result.socialAccounts().stream()
                        .map(account -> new SocialAccountResponse(account.provider(), account.providerEmail()))
                        .toList()
        );
    }

    public record SocialAccountResponse(SocialProvider provider, String providerEmail) {
    }
}
