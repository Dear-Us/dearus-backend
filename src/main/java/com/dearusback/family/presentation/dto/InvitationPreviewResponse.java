package com.dearusback.family.presentation.dto;

import com.dearusback.family.application.model.InvitationPreview;
import com.dearusback.family.domain.model.BabyInvitationStatus;
import com.dearusback.family.domain.model.BabyMemberRole;
import java.time.OffsetDateTime;

public record InvitationPreviewResponse(String babyName, String babyProfileImageUrl, BabyMemberRole role,
                                        BabyInvitationStatus status, OffsetDateTime expiresAt) {

    public static InvitationPreviewResponse from(InvitationPreview preview) {
        return new InvitationPreviewResponse(preview.babyName(), preview.babyProfileImageUrl(),
                preview.role(), preview.status(), preview.expiresAt());
    }
}
