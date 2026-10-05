package com.dearusback.family.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record BabyInvitation(Long id, Long babyId, Long createdByMemberId, String tokenHash,
                             BabyMemberRole role, BabyInvitationStatus status, Instant expiresAt,
                             Long acceptedByMemberId, Instant acceptedAt, Instant canceledAt,
                             Instant createdAt, Instant updatedAt) {

    public static final Duration VALIDITY = Duration.ofHours(72);

    public BabyInvitation {
        Objects.requireNonNull(babyId);
        Objects.requireNonNull(createdByMemberId);
        Objects.requireNonNull(tokenHash);
        Objects.requireNonNull(role);
        Objects.requireNonNull(status);
        Objects.requireNonNull(expiresAt);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static BabyInvitation create(Long babyId, Long creatorId, String tokenHash, Instant now) {
        return new BabyInvitation(null, babyId, creatorId, tokenHash, BabyMemberRole.CAREGIVER,
                BabyInvitationStatus.PENDING, now.plus(VALIDITY), null, null, null, now, now);
    }

    public boolean isExpiredAt(Instant now) {
        return status == BabyInvitationStatus.PENDING && !expiresAt.isAfter(now);
    }

    public BabyInvitation expire(Instant now) {
        if (!isExpiredAt(now)) {
            return this;
        }
        return new BabyInvitation(id, babyId, createdByMemberId, tokenHash, role,
                BabyInvitationStatus.EXPIRED, expiresAt, acceptedByMemberId, acceptedAt,
                canceledAt, createdAt, now);
    }

    public BabyInvitation cancel(Instant now) {
        if (status != BabyInvitationStatus.PENDING || !expiresAt.isAfter(now)) {
            throw new IllegalStateException("Only an unexpired pending invitation can be canceled");
        }
        return new BabyInvitation(id, babyId, createdByMemberId, tokenHash, role,
                BabyInvitationStatus.CANCELED, expiresAt, acceptedByMemberId, acceptedAt,
                now, createdAt, now);
    }
}
