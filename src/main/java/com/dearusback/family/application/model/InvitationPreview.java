package com.dearusback.family.application.model;

import com.dearusback.family.domain.model.BabyInvitationStatus;
import com.dearusback.family.domain.model.BabyMemberRole;
import java.time.OffsetDateTime;

public record InvitationPreview(String babyName, String babyProfileImageUrl, BabyMemberRole role,
                                BabyInvitationStatus status, OffsetDateTime expiresAt) {
}
