package com.dearusback.family.domain.model;

import java.time.Instant;
import java.util.Objects;

public record BabyMember(Long id, Long memberId, BabyMemberRole role, BabyMemberStatus status,
                         Instant joinedAt, Instant endedAt) {

    public BabyMember {
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(role);
        Objects.requireNonNull(status);
        Objects.requireNonNull(joinedAt);
    }

    public BabyMember remove(Instant now) {
        return status == BabyMemberStatus.ACTIVE
                ? new BabyMember(id, memberId, role, BabyMemberStatus.REMOVED, joinedAt, now)
                : this;
    }

    public BabyMember removeCaregiver(Instant now) {
        if (role != BabyMemberRole.CAREGIVER || status != BabyMemberStatus.ACTIVE) {
            throw new IllegalStateException("Only an active caregiver can be removed");
        }
        return new BabyMember(id, memberId, role, BabyMemberStatus.REMOVED, joinedAt, now);
    }

    public BabyMember leaveCaregiver(Instant now) {
        if (role != BabyMemberRole.CAREGIVER || status != BabyMemberStatus.ACTIVE) {
            throw new IllegalStateException("Only an active caregiver can leave");
        }
        return new BabyMember(id, memberId, role, BabyMemberStatus.LEFT, joinedAt, now);
    }

    public BabyMember promoteToOwner() {
        if (role != BabyMemberRole.CAREGIVER || status != BabyMemberStatus.ACTIVE) {
            throw new IllegalStateException("Only an active caregiver can become owner");
        }
        return new BabyMember(id, memberId, BabyMemberRole.OWNER, status, joinedAt, endedAt);
    }

    public BabyMember demoteToCaregiver() {
        if (role != BabyMemberRole.OWNER || status != BabyMemberStatus.ACTIVE) {
            throw new IllegalStateException("Only an active owner can become caregiver");
        }
        return new BabyMember(id, memberId, BabyMemberRole.CAREGIVER, status, joinedAt, endedAt);
    }

    public static BabyMember joinAsCaregiver(Long memberId, Instant now) {
        return new BabyMember(null, memberId, BabyMemberRole.CAREGIVER,
                BabyMemberStatus.ACTIVE, now, null);
    }

    public BabyMember reactivateAsCaregiver(Instant now) {
        if (status != BabyMemberStatus.LEFT && status != BabyMemberStatus.REMOVED) {
            throw new IllegalStateException("Only an inactive caregiver membership can be reactivated");
        }
        return new BabyMember(id, memberId, BabyMemberRole.CAREGIVER,
                BabyMemberStatus.ACTIVE, now, null);
    }
}
