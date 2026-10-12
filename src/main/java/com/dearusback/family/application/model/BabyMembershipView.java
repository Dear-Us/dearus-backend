package com.dearusback.family.application.model;

import com.dearusback.family.domain.model.BabyMemberRole;
import java.time.Instant;

public record BabyMembershipView(Long memberId, BabyMemberRole role, Instant joinedAt) {
}
