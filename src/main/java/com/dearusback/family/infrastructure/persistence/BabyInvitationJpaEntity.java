package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.domain.model.BabyInvitationStatus;
import com.dearusback.family.domain.model.BabyMemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "baby_invitation")
class BabyInvitationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invitation_id")
    private Long id;

    @Column(name = "baby_id", nullable = false)
    private Long babyId;

    @Column(name = "created_by_member_id", nullable = false)
    private Long createdByMemberId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true, columnDefinition = "char(64)")
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private BabyMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BabyInvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_by_member_id")
    private Long acceptedByMemberId;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BabyInvitationJpaEntity() {
    }

    BabyInvitationJpaEntity(Long id, Long babyId, Long createdByMemberId, String tokenHash,
                            BabyMemberRole role, BabyInvitationStatus status, Instant expiresAt,
                            Long acceptedByMemberId, Instant acceptedAt, Instant canceledAt,
                            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.babyId = babyId;
        this.createdByMemberId = createdByMemberId;
        this.tokenHash = tokenHash;
        this.role = role;
        this.status = status;
        this.expiresAt = expiresAt;
        this.acceptedByMemberId = acceptedByMemberId;
        this.acceptedAt = acceptedAt;
        this.canceledAt = canceledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    Long id() { return id; }
    Long babyId() { return babyId; }
    Long createdByMemberId() { return createdByMemberId; }
    String tokenHash() { return tokenHash; }
    BabyMemberRole role() { return role; }
    BabyInvitationStatus status() { return status; }
    Instant expiresAt() { return expiresAt; }
    Long acceptedByMemberId() { return acceptedByMemberId; }
    Instant acceptedAt() { return acceptedAt; }
    Instant canceledAt() { return canceledAt; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
