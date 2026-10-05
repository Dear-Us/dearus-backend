package com.dearusback.care.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearusback.care.domain.model.CareRecord.BreastSide;
import com.dearusback.care.domain.model.CareRecord.CareRecordInputType;
import com.dearusback.care.domain.model.CareRecord.CareRecordStatus;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord.FeedingDetail;
import com.dearusback.care.domain.model.CareRecord.FeedingType;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class CareRecordTest {

    private static final Instant NOW = Instant.parse("2026-10-05T04:00:00Z");
    private static final OffsetDateTime BEFORE = OffsetDateTime.parse("2026-10-05T10:00:00+09:00");

    @Test
    void manualCompletedRecordOwnsDetailAndAudit() {
        FeedingDetail detail = new FeedingDetail(FeedingType.DIRECT_BREASTFEEDING, null, BreastSide.BOTH, 20);
        CareRecord record = CareRecord.completed(1L, 2L, CareRecordType.FEEDING,
                CareRecordInputType.MANUAL, BEFORE, null, null, null, detail, NOW);
        assertThat(record.status()).isEqualTo(CareRecordStatus.COMPLETED);
        assertThat(record.detail()).isEqualTo(detail);
        assertThat(record.createdByMemberId()).isEqualTo(2L);
        assertThat(record.completedByMemberId()).isEqualTo(2L);
        assertThat(record.lastUpdatedByMemberId()).isEqualTo(2L);
        assertThat(record.deletedByMemberId()).isNull();
        assertThat(record.version()).isZero();
    }

    @Test
    void sleepUsesStartAsRecordedAtAndRejectsFutureOrReversedTime() {
        OffsetDateTime end = OffsetDateTime.parse("2026-10-05T12:00:00+09:00");
        CareRecord sleep = CareRecord.completed(1L, 2L, CareRecordType.SLEEP,
                CareRecordInputType.MANUAL, null, BEFORE, end, null, null, NOW);
        assertThat(sleep.recordedAt()).isEqualTo(BEFORE);
        assertThat(sleep.detail()).isNull();
        assertThatThrownBy(() -> CareRecord.completed(1L, 2L, CareRecordType.SLEEP,
                CareRecordInputType.MANUAL, null, end, BEFORE, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CareRecord.completed(1L, 2L, CareRecordType.SLEEP,
                CareRecordInputType.MANUAL, null, BEFORE, end.plusHours(2), null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrongDetailAndInvalidFeedingCombinationsAreRejected() {
        FeedingDetail formula = new FeedingDetail(FeedingType.FORMULA, 120, null, null);
        assertThatThrownBy(() -> CareRecord.completed(1L, 2L, CareRecordType.MEDICINE,
                CareRecordInputType.MANUAL, BEFORE, null, null, null, formula, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedingDetail(FeedingType.FORMULA, 120, BreastSide.LEFT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeedingDetail(FeedingType.DIRECT_BREASTFEEDING, null,
                BreastSide.BOTH, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
