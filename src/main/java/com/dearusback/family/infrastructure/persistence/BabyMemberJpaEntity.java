package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.family.domain.model.BabyMemberStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "baby_member")
class BabyMemberJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "baby_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baby_id", nullable = false)
    private BabyJpaEntity baby;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private BabyMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BabyMemberStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected BabyMemberJpaEntity() {
    }

    BabyMemberJpaEntity(Long id, Long memberId, BabyMemberRole role, BabyMemberStatus status,
                        Instant joinedAt, Instant endedAt) {
        this.id = id;
        this.memberId = memberId;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
        this.endedAt = endedAt;
    }

    void assignBaby(BabyJpaEntity baby) { this.baby = baby; }
    void updateStatus(BabyMemberStatus newStatus, Instant newEndedAt) {
        status = newStatus;
        endedAt = newEndedAt;
    }
    Long id() { return id; }
    Long memberId() { return memberId; }
    BabyMemberRole role() { return role; }
    BabyMemberStatus status() { return status; }
    Instant joinedAt() { return joinedAt; }
    Instant endedAt() { return endedAt; }
}
