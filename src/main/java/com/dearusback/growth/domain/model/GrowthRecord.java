package com.dearusback.growth.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record GrowthRecord(Long id, Long babyId, LocalDate measuredDate, BigDecimal heightCm, BigDecimal weightKg,
                           String memo, Long createdByMemberId, Long lastUpdatedByMemberId,
                           Long deletedByMemberId, long version, boolean deleted, Instant createdAt,
                           Instant updatedAt, Instant deletedAt) {

    public GrowthRecord {
        Objects.requireNonNull(babyId);
        Objects.requireNonNull(measuredDate);
        Objects.requireNonNull(createdByMemberId);
        Objects.requireNonNull(lastUpdatedByMemberId);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (version < 0 || (heightCm == null && weightKg == null)
                || (heightCm != null && heightCm.signum() <= 0)
                || (weightKg != null && weightKg.signum() <= 0)) {
            throw new IllegalArgumentException("Invalid growth measurement");
        }
    }

    public static GrowthRecord create(Long babyId, Long memberId, LocalDate measuredDate,
                                      BigDecimal heightCm, BigDecimal weightKg, String memo, Instant now) {
        return new GrowthRecord(null, babyId, measuredDate, heightCm, weightKg, memo, memberId, memberId,
                null, 0, false, now, now, null);
    }

    public GrowthRecord edit(LocalDate measuredDate, BigDecimal heightCm, BigDecimal weightKg,
                             String memo, Long memberId, Instant now) {
        return new GrowthRecord(id, babyId, measuredDate, heightCm, weightKg, memo, createdByMemberId,
                memberId, null, version, false, createdAt, now, null);
    }

    public GrowthRecord softDelete(Long memberId, Instant now) {
        return new GrowthRecord(id, babyId, measuredDate, heightCm, weightKg, memo, createdByMemberId,
                memberId, memberId, version, true, createdAt, now, now);
    }

    public GrowthRecord persisted(Long persistedId, long persistedVersion) {
        return new GrowthRecord(persistedId, babyId, measuredDate, heightCm, weightKg, memo, createdByMemberId,
                lastUpdatedByMemberId, deletedByMemberId, persistedVersion, deleted, createdAt, updatedAt,
                deletedAt);
    }
}
