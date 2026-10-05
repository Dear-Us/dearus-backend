package com.dearusback.family.application.model;

import com.dearusback.family.domain.model.BabyInvitationStatus;
import com.dearusback.family.domain.model.BabyMemberRole;
import java.time.OffsetDateTime;

public record AcceptedInvitation(Long invitationId, Long babyId, String babyName,
                                 BabyMemberRole role, BabyInvitationStatus status,
                                 OffsetDateTime acceptedAt) {
}
