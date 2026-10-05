package com.dearusback.family.presentation.dto;

import com.dearusback.family.application.model.InvitationSummary;
import com.dearusback.family.domain.model.BabyInvitationStatus;
import com.dearusback.family.domain.model.BabyMemberRole;
import java.time.OffsetDateTime;

public record InvitationSummaryResponse(Long invitationId, BabyMemberRole role, BabyInvitationStatus status,
                                        OffsetDateTime createdAt, OffsetDateTime expiresAt,
                                        OffsetDateTime acceptedAt, OffsetDateTime canceledAt) {

    public static InvitationSummaryResponse from(InvitationSummary summary) {
        return new InvitationSummaryResponse(summary.invitationId(), summary.role(), summary.status(),
                summary.createdAt(), summary.expiresAt(), summary.acceptedAt(), summary.canceledAt());
    }
}
