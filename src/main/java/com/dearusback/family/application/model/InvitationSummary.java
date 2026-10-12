package com.dearusback.family.application.model;

import com.dearusback.family.domain.model.BabyInvitation;
import com.dearusback.family.domain.model.BabyInvitationStatus;
import com.dearusback.family.domain.model.BabyMemberRole;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record InvitationSummary(Long invitationId, BabyMemberRole role, BabyInvitationStatus status,
                                OffsetDateTime createdAt, OffsetDateTime expiresAt,
                                OffsetDateTime acceptedAt, OffsetDateTime canceledAt) {

    public static InvitationSummary from(BabyInvitation invitation, ZoneId zone) {
        return new InvitationSummary(invitation.id(), invitation.role(), invitation.status(),
                invitation.createdAt().atZone(zone).toOffsetDateTime(),
                invitation.expiresAt().atZone(zone).toOffsetDateTime(),
                invitation.acceptedAt() == null ? null : invitation.acceptedAt().atZone(zone).toOffsetDateTime(),
                invitation.canceledAt() == null ? null : invitation.canceledAt().atZone(zone).toOffsetDateTime());
    }
}
