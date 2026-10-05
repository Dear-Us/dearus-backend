package com.dearusback.family.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BabyInvitationTest {

    private static final Instant CREATED = Instant.parse("2026-10-05T00:00:00Z");

    @Test
    void expiresAtExactly72HoursAndPreservesTerminalStates() {
        BabyInvitation pending = BabyInvitation.create(1L, 2L, "a".repeat(64), CREATED);
        Instant deadline = CREATED.plus(BabyInvitation.VALIDITY);
        assertThat(pending.expiresAt()).isEqualTo(deadline);
        assertThat(pending.isExpiredAt(deadline.minusNanos(1))).isFalse();
        assertThat(pending.isExpiredAt(deadline)).isTrue();
        BabyInvitation expired = pending.expire(deadline);
        assertThat(expired.status()).isEqualTo(BabyInvitationStatus.EXPIRED);
        assertThat(expired.updatedAt()).isEqualTo(deadline);
        assertThat(expired.expire(deadline.plusSeconds(1))).isSameAs(expired);
        assertThat(expired.canceledAt()).isNull();
    }

    @Test
    void onlyPendingAndUnexpiredInvitationCanBeCanceled() {
        BabyInvitation pending = BabyInvitation.create(1L, 2L, "b".repeat(64), CREATED);
        BabyInvitation canceled = pending.cancel(CREATED.plusSeconds(1));
        assertThat(canceled.status()).isEqualTo(BabyInvitationStatus.CANCELED);
        assertThat(canceled.canceledAt()).isEqualTo(CREATED.plusSeconds(1));
        assertThat(canceled.isExpiredAt(CREATED.plusSeconds(73 * 3600))).isFalse();
        assertThatThrownBy(() -> canceled.cancel(CREATED.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.cancel(pending.expiresAt()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.expire(pending.expiresAt()).cancel(pending.expiresAt()))
                .isInstanceOf(IllegalStateException.class);
    }
}
