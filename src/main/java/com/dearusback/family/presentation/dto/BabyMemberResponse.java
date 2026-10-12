package com.dearusback.family.presentation.dto;

import com.dearusback.family.application.model.BabyMemberSummary;
import com.dearusback.family.domain.model.BabyMemberRole;

public record BabyMemberResponse(Long memberId, String memberName, String profileImageUrl,
                                 BabyMemberRole role) {
    public static BabyMemberResponse from(BabyMemberSummary summary) {
        return new BabyMemberResponse(summary.memberId(), summary.memberName(),
                summary.profileImageUrl(), summary.role());
    }
}
