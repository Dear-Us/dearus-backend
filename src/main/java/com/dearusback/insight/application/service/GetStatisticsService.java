package com.dearusback.insight.application.service;

import com.dearusback.care.application.model.CareStatisticsSource;
import com.dearusback.care.application.port.in.CareStatisticsQuery;
import com.dearusback.family.application.port.in.ActiveBabyMembershipAccess;
import com.dearusback.growth.application.model.GrowthMeasurement;
import com.dearusback.growth.application.port.in.GrowthMeasurementsQuery;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import com.dearusback.insight.application.model.DailyStatistics;
import com.dearusback.insight.application.model.GrowthStatistics;
import com.dearusback.insight.application.model.RoutineStatistics;
import com.dearusback.insight.application.port.in.GetStatisticsUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetStatisticsService implements GetStatisticsUseCase {
    private final BabyRegistrationMemberAccess members;
    private final ActiveBabyMembershipAccess babies;
    private final CareStatisticsQuery care;
    private final GrowthMeasurementsQuery growth;

    public GetStatisticsService(BabyRegistrationMemberAccess members, ActiveBabyMembershipAccess babies,
                                CareStatisticsQuery care, GrowthMeasurementsQuery growth) {
        this.members = members;
        this.babies = babies;
        this.care = care;
        this.growth = growth;
    }

    @Override
    public DailyStatistics daily(Long memberId, Long babyId, LocalDate date) {
        ZoneId zone = authorize(memberId, babyId);
        return CareStatisticsCalculator.daily(date, zone, load(babyId, date, date, zone));
    }

    @Override
    public RoutineStatistics routine(Long memberId, Long babyId, LocalDate endDate) {
        ZoneId zone = authorize(memberId, babyId);
        LocalDate startDate = endDate.minusDays(6);
        CareStatisticsSource source = load(babyId, startDate, endDate, zone);
        List<RoutineStatistics.Day> days = new ArrayList<>(7);
        long feeding = 0;
        long formulaMl = 0;
        long directMinutes = 0;
        long sleepMinutes = 0;
        long diapers = 0;
        for (int offset = 0; offset < 7; offset++) {
            LocalDate day = startDate.plusDays(offset);
            DailyStatistics daily = CareStatisticsCalculator.daily(day, zone, source);
            RoutineStatistics.Day point = new RoutineStatistics.Day(day, daily.feeding().totalCount(),
                    daily.feeding().formulaTotalMl(), daily.feeding().directBreastfeedingTotalMinutes(),
                    daily.sleep().totalMinutes(), daily.diaper().totalCount());
            days.add(point);
            feeding += point.feedingCount();
            formulaMl += point.formulaTotalMl();
            directMinutes += point.directBreastfeedingMinutes();
            sleepMinutes += point.sleepTotalMinutes();
            diapers += point.diaperCount();
        }
        return new RoutineStatistics(startDate, endDate, List.copyOf(days), new RoutineStatistics.Averages(
                CareStatisticsCalculator.average(feeding, 7), CareStatisticsCalculator.average(formulaMl, 7),
                CareStatisticsCalculator.average(directMinutes, 7),
                CareStatisticsCalculator.average(sleepMinutes, 7),
                CareStatisticsCalculator.average(diapers, 7)));
    }

    @Override
    public GrowthStatistics growth(Long memberId, Long babyId) {
        authorize(memberId, babyId);
        List<GrowthMeasurement> points = growth.findAll(babyId);
        if (points.isEmpty()) {
            return new GrowthStatistics(null, new GrowthStatistics.ChangeFromPrevious(null, null), points);
        }
        GrowthMeasurement latest = points.getLast();
        BigDecimal previousHeight = previous(points, true);
        BigDecimal previousWeight = previous(points, false);
        return new GrowthStatistics(new GrowthStatistics.Latest(latest.measuredDate(),
                latest.heightCm(), latest.weightKg()), new GrowthStatistics.ChangeFromPrevious(
                latest.heightCm() == null || previousHeight == null ? null : latest.heightCm().subtract(previousHeight),
                latest.weightKg() == null || previousWeight == null ? null : latest.weightKg().subtract(previousWeight)),
                points);
    }

    private BigDecimal previous(List<GrowthMeasurement> points, boolean height) {
        for (int index = points.size() - 2; index >= 0; index--) {
            BigDecimal value = height ? points.get(index).heightCm() : points.get(index).weightKg();
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private CareStatisticsSource load(Long babyId, LocalDate startDate, LocalDate endDate, ZoneId zone) {
        Instant start = startDate.atStartOfDay(zone).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(zone).toInstant();
        return care.load(babyId, start, end);
    }

    private ZoneId authorize(Long memberId, Long babyId) {
        ZoneId zone = ZoneId.of(members.requireActiveMember(memberId).timezone());
        babies.requireActiveMembership(memberId, babyId);
        return zone;
    }
}
