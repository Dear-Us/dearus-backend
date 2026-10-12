package com.dearusback.family.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BabyCaregiverConnectionTest {

    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");

    @Test
    void connectsNewCaregiverAndReactivatesOldRowWithoutChangingItsId() {
        Baby baby = persistedBaby();
        Baby connected = baby.connectCaregiver(2L, NOW.plusSeconds(1));
        BabyMember newMember = connected.members().get(1);
        assertThat(newMember.role()).isEqualTo(BabyMemberRole.CAREGIVER);
        assertThat(newMember.status()).isEqualTo(BabyMemberStatus.ACTIVE);
        assertThat(newMember.joinedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(newMember.endedAt()).isNull();

        BabyMember old = new BabyMember(8L, 3L, BabyMemberRole.CAREGIVER,
                BabyMemberStatus.LEFT, NOW, NOW.plusSeconds(2));
        Baby existing = new Baby(5L, 1L, "도윤", LocalDate.of(2020, 1, 1), BabyGender.MALE,
                null, false, NOW, NOW, null, List.of(baby.members().getFirst(), old));
        BabyMember reactivated = existing.connectCaregiver(3L, NOW.plusSeconds(3)).members().get(1);
        assertThat(reactivated.id()).isEqualTo(8L);
        assertThat(reactivated.status()).isEqualTo(BabyMemberStatus.ACTIVE);
        assertThat(reactivated.joinedAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(reactivated.endedAt()).isNull();
    }

    @Test
    void rejectsActiveMembershipAndDeletedBaby() {
        Baby baby = persistedBaby();
        assertThatThrownBy(() -> baby.connectCaregiver(1L, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> baby.markDeleted(NOW.plusSeconds(1)).connectCaregiver(2L, NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    private Baby persistedBaby() {
        Baby registered = Baby.register(1L, "도윤", LocalDate.of(2020, 1, 1), BabyGender.MALE, null, NOW);
        return new Baby(5L, 1L, "도윤", LocalDate.of(2020, 1, 1), BabyGender.MALE,
                null, false, NOW, NOW, null, registered.members());
    }
}
