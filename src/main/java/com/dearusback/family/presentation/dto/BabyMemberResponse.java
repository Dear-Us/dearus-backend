package com.dearusback.family.presentation.dto;

import com.dearusback.family.application.model.BabyMemberSummary;
import com.dearusback.family.domain.model.BabyMemberRole;
import java.util.function.BiFunction;

public record BabyMemberResponse(Long memberId, String memberName, String profileImageUrl,
                                 BabyMemberRole role) {
    public static BabyMemberResponse from(BabyMemberSummary summary,
                                          BiFunction<Long, String, String> imageUrlReader) {
        return new BabyMemberResponse(summary.memberId(), summary.memberName(),
                imageUrlReader.apply(summary.memberId(), summary.profileImageUrl()), summary.role());
    }
}
