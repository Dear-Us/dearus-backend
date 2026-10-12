package com.dearusback.family.presentation.dto;

import com.dearusback.family.application.model.AcceptedInvitation;
import com.dearusback.family.domain.model.BabyInvitationStatus;
import com.dearusback.family.domain.model.BabyMemberRole;
import java.time.OffsetDateTime;

public record AcceptedInvitationResponse(Long invitationId, Long babyId, String babyName,
                                         BabyMemberRole role, BabyInvitationStatus status,
                                         OffsetDateTime acceptedAt) {

    public static AcceptedInvitationResponse from(AcceptedInvitation accepted) {
        return new AcceptedInvitationResponse(accepted.invitationId(), accepted.babyId(), accepted.babyName(),
                accepted.role(), accepted.status(), accepted.acceptedAt());
    }
}
