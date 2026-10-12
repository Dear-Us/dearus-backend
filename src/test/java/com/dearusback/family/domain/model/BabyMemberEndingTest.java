package com.dearusback.family.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BabyMemberEndingTest {

    private static final Instant JOINED = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant ENDED = Instant.parse("2026-10-05T00:00:00Z");

    @Test
    void ownerRemovalMarksOnlyActiveCaregiverRemoved() {
        BabyMember active = new BabyMember(9L, 2L, BabyMemberRole.CAREGIVER,
                BabyMemberStatus.ACTIVE, JOINED, null);
        BabyMember removed = active.removeCaregiver(ENDED);
        assertThat(removed.id()).isEqualTo(9L);
        assertThat(removed.memberId()).isEqualTo(2L);
        assertThat(removed.role()).isEqualTo(BabyMemberRole.CAREGIVER);
        assertThat(removed.status()).isEqualTo(BabyMemberStatus.REMOVED);
        assertThat(removed.joinedAt()).isEqualTo(JOINED);
        assertThat(removed.endedAt()).isEqualTo(ENDED);
        assertThatThrownBy(() -> removed.removeCaregiver(ENDED)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BabyMember(10L, 3L, BabyMemberRole.OWNER,
                BabyMemberStatus.ACTIVE, JOINED, null).removeCaregiver(ENDED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void selfLeaveMarksOnlyActiveCaregiverLeft() {
        BabyMember active = new BabyMember(9L, 2L, BabyMemberRole.CAREGIVER,
                BabyMemberStatus.ACTIVE, JOINED, null);
        BabyMember left = active.leaveCaregiver(ENDED);
        assertThat(left.status()).isEqualTo(BabyMemberStatus.LEFT);
        assertThat(left.endedAt()).isEqualTo(ENDED);
        assertThatThrownBy(() -> left.leaveCaregiver(ENDED)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BabyMember(10L, 3L, BabyMemberRole.OWNER,
                BabyMemberStatus.ACTIVE, JOINED, null).leaveCaregiver(ENDED))
                .isInstanceOf(IllegalStateException.class);
    }
}
