package com.dearusback.family.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record Baby(Long id, Long createdByMemberId, String name, LocalDate birthDate, BabyGender gender,
                   String profileImageUrl, boolean deleted, Instant createdAt, Instant updatedAt,
                   Instant deletedAt, List<BabyMember> members) {

    public Baby {
        Objects.requireNonNull(createdByMemberId);
        Objects.requireNonNull(name);
        Objects.requireNonNull(birthDate);
        Objects.requireNonNull(gender);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        members = List.copyOf(members);
        if (id == null && (members.size() != 1 || members.getFirst().role() != BabyMemberRole.OWNER
                || members.getFirst().status() != BabyMemberStatus.ACTIVE
                || !members.getFirst().memberId().equals(createdByMemberId))) {
            throw new IllegalArgumentException("A new baby must have one active creator-owner");
        }
    }

    public static Baby register(Long creatorId, String name, LocalDate birthDate, BabyGender gender,
                                String profileImageUrl, Instant now) {
        return new Baby(null, creatorId, name, birthDate, gender, profileImageUrl,
                false, now, now, null, List.of(new BabyMember(null, creatorId,
                BabyMemberRole.OWNER, BabyMemberStatus.ACTIVE, now, null)));
    }

    public Baby changeInformation(String newName, LocalDate newBirthDate, BabyGender newGender,
                                  String newProfileImageUrl, Instant now) {
        if (deleted) {
            throw new IllegalStateException("Deleted baby cannot be changed");
        }
        return new Baby(id, createdByMemberId, newName, newBirthDate, newGender,
                newProfileImageUrl, deleted, createdAt, now, deletedAt, members);
    }

    public Baby markDeleted(Instant now) {
        if (deleted) {
            throw new IllegalStateException("Baby already deleted");
        }
        return new Baby(id, createdByMemberId, name, birthDate, gender, null,
                true, createdAt, now, now, members.stream().map(member -> member.remove(now)).toList());
    }
}
