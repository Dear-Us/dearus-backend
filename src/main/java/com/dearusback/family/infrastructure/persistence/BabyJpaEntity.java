package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.domain.model.BabyGender;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "baby")
class BabyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "baby_id")
    private Long id;

    @Column(name = "created_by_member_id", nullable = false)
    private Long createdByMemberId;

    @Column(name = "baby_name", nullable = false, length = 50)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private BabyGender gender;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "baby", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BabyMemberJpaEntity> members = new ArrayList<>();

    protected BabyJpaEntity() {
    }

    BabyJpaEntity(Long id, Long createdByMemberId, String name, LocalDate birthDate, BabyGender gender,
                  String profileImageUrl, boolean deleted, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this.id = id;
        this.createdByMemberId = createdByMemberId;
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.profileImageUrl = profileImageUrl;
        this.deleted = deleted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    void addMember(BabyMemberJpaEntity member) {
        members.add(member);
        member.assignBaby(this);
    }

    void updateInformation(String newName, LocalDate newBirthDate, BabyGender newGender,
                           String newProfileImageUrl, Instant now) {
        name = newName;
        birthDate = newBirthDate;
        gender = newGender;
        profileImageUrl = newProfileImageUrl;
        updatedAt = now;
    }

    Long id() { return id; }
    Long createdByMemberId() { return createdByMemberId; }
    String name() { return name; }
    LocalDate birthDate() { return birthDate; }
    BabyGender gender() { return gender; }
    String profileImageUrl() { return profileImageUrl; }
    boolean deleted() { return deleted; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
    Instant deletedAt() { return deletedAt; }
    List<BabyMemberJpaEntity> members() { return members; }
}
