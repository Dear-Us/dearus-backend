package com.dearusback.identity.domain.model;

import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import java.util.List;
import java.util.Objects;

public final class Member {

    public static final String DEFAULT_NAME = "사용자";
    public static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    private final Long id;
    private final String name;
    private final String email;
    private final String timezone;
    private final String profileImageUrl;
    private final Long lastSelectedBabyId;
    private final boolean deleted;
    private final List<SocialAccount> socialAccounts;

    public Member(
            Long id,
            String name,
            String email,
            String timezone,
            String profileImageUrl,
            Long lastSelectedBabyId,
            boolean deleted,
            List<SocialAccount> socialAccounts
    ) {
        this.id = id;
        this.name = requireText(name, "name");
        this.email = email;
        this.timezone = requireText(timezone, "timezone");
        this.profileImageUrl = profileImageUrl;
        this.lastSelectedBabyId = lastSelectedBabyId;
        this.deleted = deleted;
        this.socialAccounts = List.copyOf(Objects.requireNonNull(socialAccounts, "socialAccounts must not be null"));
    }

    public static Member register(
            String name,
            String email,
            String profileImageUrl,
            SocialAccount socialAccount
    ) {
        String initialName = name == null || name.isBlank() ? DEFAULT_NAME : name;
        return new Member(
                null,
                initialName,
                email,
                DEFAULT_TIMEZONE,
                profileImageUrl,
                null,
                false,
                List.of(socialAccount)
        );
    }

    public void updateProviderEmail(String providerUserId, String providerEmail) {
        socialAccounts.stream()
                .filter(account -> account.providerUserId().equals(providerUserId))
                .findFirst()
                .ifPresent(account -> account.updateProviderEmail(providerEmail));
    }

    public Member changeName(String newName) {
        return copy(normalizeName(newName), profileImageUrl);
    }

    public Member changeProfileImage(String newImageUrl) {
        return copy(name, requireText(newImageUrl, "profileImageUrl"));
    }

    public Member removeProfileImage() {
        return copy(name, null);
    }

    public static String normalizeName(String value) {
        if (value == null) {
            throw new IdentityException(IdentityErrorCode.INVALID_MEMBER_NAME);
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || trimmed.codePointCount(0, trimmed.length()) > 50) {
            throw new IdentityException(IdentityErrorCode.INVALID_MEMBER_NAME);
        }
        return trimmed;
    }

    private Member copy(String newName, String newProfileImageUrl) {
        return new Member(id, newName, email, timezone, newProfileImageUrl,
                lastSelectedBabyId, deleted, socialAccounts);
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String timezone() {
        return timezone;
    }

    public String profileImageUrl() {
        return profileImageUrl;
    }

    public Long lastSelectedBabyId() {
        return lastSelectedBabyId;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public List<SocialAccount> socialAccounts() {
        return socialAccounts;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
