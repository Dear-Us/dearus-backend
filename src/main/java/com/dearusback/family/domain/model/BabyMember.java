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
}
