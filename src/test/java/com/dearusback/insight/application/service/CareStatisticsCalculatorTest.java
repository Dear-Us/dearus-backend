package com.dearusback.insight.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearusback.care.application.model.CareStatisticsSource;
import com.dearusback.care.application.model.CareStatisticsSource.SleepInterval;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareStatisticsCalculatorTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void emptyDayUsesZeroForCountsAndNullForUncomputableValues() {
        var result = CareStatisticsCalculator.daily(LocalDate.of(2025, 10, 5), SEOUL,
                new CareStatisticsSource(List.of(), List.of()));

        assertThat(result.feeding().totalCount()).isZero();
        assertThat(result.feeding().formulaTotalMl()).isZero();
        assertThat(result.feeding().formulaAverageMl()).isNull();
        assertThat(result.feeding().directBreastfeedingAverageMinutes()).isNull();
        assertThat(result.sleep().count()).isZero();
        assertThat(result.sleep().totalMinutes()).isZero();
        assertThat(result.sleep().averageMinutes()).isNull();
        assertThat(result.diaper().totalCount()).isZero();
        assertThat(result.temperature().count()).isZero();
        assertThat(result.temperature().latestTemperatureCelsius()).isNull();
        assertThat(result.temperature().minTemperatureCelsius()).isNull();
        assertThat(result.temperature().maxTemperatureCelsius()).isNull();
        assertThat(result.medicine().count()).isZero();
        assertThat(result.other().count()).isZero();
    }

    @Test
    void sleepOverlapAccumulatesSecondsBeforeFlooringMinutes() {
        var source = new CareStatisticsSource(List.of(), List.of(
                new SleepInterval(Instant.parse("2025-10-04T14:59:30Z"),
                        Instant.parse("2025-10-04T15:00:30Z")),
                new SleepInterval(Instant.parse("2025-10-04T15:00:00Z"),
                        Instant.parse("2025-10-04T15:00:40Z")),
                new SleepInterval(Instant.parse("2025-10-05T15:00:00Z"),
                        Instant.parse("2025-10-05T16:00:00Z"))));

        var result = CareStatisticsCalculator.daily(LocalDate.of(2025, 10, 5), SEOUL, source);

        assertThat(result.sleep().count()).isEqualTo(2);
        assertThat(result.sleep().totalMinutes()).isEqualTo(1);
        assertThat(result.sleep().averageMinutes()).isEqualByComparingTo("0.5");
    }

    @Test
    void averagesRoundHalfUpToOneDecimal() {
        assertThat(CareStatisticsCalculator.average(1, 7)).isEqualByComparingTo("0.1");
        assertThat(CareStatisticsCalculator.average(4, 7)).isEqualByComparingTo("0.6");
        assertThat(CareStatisticsCalculator.average(0, 7)).isEqualByComparingTo("0.0");
        assertThat(CareStatisticsCalculator.average(0, 0)).isNull();
    }
}
