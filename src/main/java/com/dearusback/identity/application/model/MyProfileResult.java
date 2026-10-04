package com.dearusback.identity.application.model;

import com.dearusback.identity.domain.type.SocialProvider;
import java.util.List;

public record MyProfileResult(
        Long memberId,
        String email,
        String memberName,
        String profileImageUrl,
        String timezone,
        Long lastSelectedBabyId,
        List<SocialAccountResult> socialAccounts
) {

    public record SocialAccountResult(SocialProvider provider, String providerEmail) {
    }
}
