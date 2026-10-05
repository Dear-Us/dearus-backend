package com.dearusback.growth.infrastructure.persistence;

import com.dearusback.growth.domain.model.GrowthRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "growth_record")
class GrowthRecordJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "growth_record_id")
    private Long id;

    @Column(name = "baby_id", nullable = false)
    private Long babyId;

    @Column(name = "measured_date", nullable = false)
    private LocalDate measuredDate;

    @Column(name = "height_cm", precision = 65, scale = 30)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 65, scale = 30)
    private BigDecimal weightKg;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "created_by_member_id", nullable = false)
    private Long createdByMemberId;

    @Column(name = "last_updated_by_member_id", nullable = false)
    private Long lastUpdatedByMemberId;

    @Column(name = "deleted_by_member_id")
    private Long deletedByMemberId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected GrowthRecordJpaEntity() {
    }

    GrowthRecordJpaEntity(GrowthRecord record) {
        babyId = record.babyId();
        measuredDate = record.measuredDate();
        heightCm = record.heightCm();
        weightKg = record.weightKg();
        memo = record.memo();
        createdByMemberId = record.createdByMemberId();
        lastUpdatedByMemberId = record.lastUpdatedByMemberId();
        deletedByMemberId = record.deletedByMemberId();
        deleted = record.deleted();
        createdAt = record.createdAt();
        updatedAt = record.updatedAt();
        deletedAt = record.deletedAt();
    }

    Long id() { return id; }
    long version() { return version; }

    GrowthRecord toDomain() {
        return new GrowthRecord(id, babyId, measuredDate, heightCm, weightKg, memo, createdByMemberId,
                lastUpdatedByMemberId, deletedByMemberId, version, deleted, createdAt, updatedAt, deletedAt);
    }

    void apply(GrowthRecord record) {
        measuredDate = record.measuredDate();
        heightCm = record.heightCm();
        weightKg = record.weightKg();
        memo = record.memo();
        lastUpdatedByMemberId = record.lastUpdatedByMemberId();
        deletedByMemberId = record.deletedByMemberId();
        deleted = record.deleted();
        updatedAt = record.updatedAt();
        deletedAt = record.deletedAt();
    }
}
