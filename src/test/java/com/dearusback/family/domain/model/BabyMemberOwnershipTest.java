package com.dearusback.family.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BabyMemberOwnershipTest {

    private static final Instant JOINED = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void transferChangesOnlyActiveRoles() {
        BabyMember owner = new BabyMember(1L, 11L, BabyMemberRole.OWNER, BabyMemberStatus.ACTIVE, JOINED, null);
        BabyMember caregiver = new BabyMember(2L, 22L, BabyMemberRole.CAREGIVER,
                BabyMemberStatus.ACTIVE, JOINED, null);
        assertThat(owner.demoteToCaregiver()).isEqualTo(new BabyMember(1L, 11L,
                BabyMemberRole.CAREGIVER, BabyMemberStatus.ACTIVE, JOINED, null));
        assertThat(caregiver.promoteToOwner()).isEqualTo(new BabyMember(2L, 22L,
                BabyMemberRole.OWNER, BabyMemberStatus.ACTIVE, JOINED, null));
    }

    @Test
    void inactiveOrWrongRoleCannotBeTransferred() {
        BabyMember owner = new BabyMember(1L, 11L, BabyMemberRole.OWNER, BabyMemberStatus.ACTIVE, JOINED, null);
        BabyMember caregiver = new BabyMember(2L, 22L, BabyMemberRole.CAREGIVER,
                BabyMemberStatus.ACTIVE, JOINED, null);
        assertThatThrownBy(owner::promoteToOwner).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(caregiver::demoteToCaregiver).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> caregiver.leaveCaregiver(JOINED).promoteToOwner())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> owner.remove(JOINED).demoteToCaregiver())
                .isInstanceOf(IllegalStateException.class);
    }
}
