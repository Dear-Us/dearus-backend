package com.dearusback.family.application.model;

import com.dearusback.family.domain.model.BabyMemberRole;

public record BabyMemberSummary(Long memberId, String memberName, String profileImageUrl,
                                BabyMemberRole role) {
}
