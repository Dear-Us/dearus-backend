package com.dearusback.identity.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "member")
class MemberJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "member_name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    @Column(name = "last_selected_baby_id")
    private Long lastSelectedBabyId;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SocialAccountJpaEntity> socialAccounts = new ArrayList<>();

    protected MemberJpaEntity() {
    }

    MemberJpaEntity(
            Long id,
            String name,
            String email,
            String profileImageUrl,
            Long lastSelectedBabyId,
            boolean deleted
    ) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.lastSelectedBabyId = lastSelectedBabyId;
        this.deleted = deleted;
    }

    void addSocialAccount(SocialAccountJpaEntity socialAccount) {
        socialAccounts.add(socialAccount);
        socialAccount.assignMember(this);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    Long id() {
        return id;
    }

    String name() {
        return name;
    }

    String email() {
        return email;
    }

    String profileImageUrl() {
        return profileImageUrl;
    }

    Long lastSelectedBabyId() {
        return lastSelectedBabyId;
    }

    boolean deleted() {
        return deleted;
    }

    List<SocialAccountJpaEntity> socialAccounts() {
        return socialAccounts;
    }
}
