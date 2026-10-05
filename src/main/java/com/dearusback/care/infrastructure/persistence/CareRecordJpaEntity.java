package com.dearusback.care.infrastructure.persistence;

import com.dearusback.care.domain.model.CareRecord.CareRecordInputType;
import com.dearusback.care.domain.model.CareRecord.CareRecordStatus;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "care_record")
class CareRecordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long id;

    @Column(name = "baby_id", nullable = false)
    private Long babyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 20)
    private CareRecordType recordType;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, length = 20)
    private CareRecordInputType inputType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CareRecordStatus status;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "created_by_member_id", nullable = false)
    private Long createdByMemberId;

    @Column(name = "completed_by_member_id")
    private Long completedByMemberId;

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

    protected CareRecordJpaEntity() {
    }

    CareRecordJpaEntity(com.dearusback.care.domain.model.CareRecord record) {
        babyId = record.babyId();
        recordType = record.recordType();
        inputType = record.inputType();
        status = record.status();
        recordedAt = record.recordedAt().toInstant();
        startAt = record.startAt() == null ? null : record.startAt().toInstant();
        endAt = record.endAt() == null ? null : record.endAt().toInstant();
        memo = record.memo();
        createdByMemberId = record.createdByMemberId();
        completedByMemberId = record.completedByMemberId();
        lastUpdatedByMemberId = record.lastUpdatedByMemberId();
        deletedByMemberId = record.deletedByMemberId();
        deleted = record.deleted();
        createdAt = record.createdAt();
        updatedAt = record.updatedAt();
        deletedAt = record.deletedAt();
    }

    Long id() { return id; }
    long version() { return version; }
}
