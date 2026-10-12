package com.dearusback.family.presentation.dto;

import com.dearusback.family.application.model.CreatedInvitation;
import com.dearusback.family.domain.model.BabyInvitationStatus;
import com.dearusback.family.domain.model.BabyMemberRole;
import java.time.OffsetDateTime;

public record CreateBabyInvitationResponse(Long invitationId, String invitationUrl, BabyMemberRole role,
                                           BabyInvitationStatus status, OffsetDateTime expiresAt) {
    public static CreateBabyInvitationResponse from(CreatedInvitation result) {
        return new CreateBabyInvitationResponse(result.invitationId(), result.invitationUrl(),
                result.role(), result.status(), result.expiresAt());
    }
}
