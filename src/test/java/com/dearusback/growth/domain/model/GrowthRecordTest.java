package com.dearusback.growth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GrowthRecordTest {
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    void creationKeepsMeasurementAndCreatorAudit() {
        GrowthRecord created = GrowthRecord.create(10L, 20L, LocalDate.of(2026, 10, 5),
                new BigDecimal("61.5"), null, "측정", NOW);

        assertThat(created.heightCm()).isEqualByComparingTo("61.5");
        assertThat(created.weightKg()).isNull();
        assertThat(created.createdByMemberId()).isEqualTo(20L);
        assertThat(created.lastUpdatedByMemberId()).isEqualTo(20L);
        assertThat(created.version()).isZero();
    }

    @Test
    void invalidMeasurementsAreRejected() {
        for (BigDecimal invalid : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-1")}) {
            assertThatThrownBy(() -> GrowthRecord.create(1L, 2L, LocalDate.of(2026, 10, 5),
                    invalid, null, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> GrowthRecord.create(1L, 2L, LocalDate.of(2026, 10, 5),
                null, null, null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editingAndDeletingPreserveCreatorAndUpdateAudit() {
        GrowthRecord existing = GrowthRecord.create(1L, 2L, LocalDate.of(2026, 10, 5),
                new BigDecimal("60"), null, "old", NOW).persisted(3L, 0);

        GrowthRecord edited = existing.edit(LocalDate.of(2026, 10, 4), null,
                new BigDecimal("6.8"), null, 4L, NOW.plusSeconds(1));
        GrowthRecord deleted = edited.softDelete(5L, NOW.plusSeconds(2));

        assertThat(edited.createdByMemberId()).isEqualTo(2L);
        assertThat(edited.lastUpdatedByMemberId()).isEqualTo(4L);
        assertThat(deleted.createdByMemberId()).isEqualTo(2L);
        assertThat(deleted.lastUpdatedByMemberId()).isEqualTo(5L);
        assertThat(deleted.deletedByMemberId()).isEqualTo(5L);
        assertThat(deleted.deletedAt()).isEqualTo(NOW.plusSeconds(2));
    }
}
