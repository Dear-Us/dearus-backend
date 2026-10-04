package com.dearusback.identity.application.model;

import com.dearusback.identity.domain.type.SocialProvider;
import com.dearusback.identity.domain.model.Member;
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

    public static MyProfileResult from(Member member) {
        return new MyProfileResult(
                member.id(),
                member.email(),
                member.name(),
                member.profileImageUrl(),
                member.timezone(),
                member.lastSelectedBabyId(),
                member.socialAccounts().stream()
                        .map(account -> new SocialAccountResult(account.provider(), account.providerEmail()))
                        .toList()
        );
    }

    public record SocialAccountResult(SocialProvider provider, String providerEmail) {
    }
}
