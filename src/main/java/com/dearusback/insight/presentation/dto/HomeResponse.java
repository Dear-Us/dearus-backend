package com.dearusback.insight.presentation.dto;

import com.dearusback.care.application.model.CareRecordResponse;
import com.dearusback.care.application.model.CareTypeCounts;
import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.insight.application.model.HomeResult;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

public record HomeResponse(Baby baby, CareRecordResponse activeRecord, TodaySummary todaySummary,
                           CareRecordResponse lastCompletedRecord, List<CareRecordResponse> recentRecords) {

    public static HomeResponse from(HomeResult result, Function<String, String> imageUrlReader) {
        var snapshot = result.care();
        List<CareRecordResponse> recent = snapshot.recentRecords().stream()
                .map(CareRecordResponse::from).toList();
        return new HomeResponse(Baby.from(result.baby(), imageUrlReader),
                snapshot.activeRecord() == null ? null : CareRecordResponse.from(snapshot.activeRecord()),
                TodaySummary.from(result.date(), snapshot.todayCounts()),
                recent.isEmpty() ? null : recent.getFirst(), recent);
    }

    public record Baby(Long babyId, String babyName, LocalDate birthDate, String gender,
                       String profileImageUrl, String myRole) {
        static Baby from(MyBabySummary baby, Function<String, String> imageUrlReader) {
            return new Baby(baby.babyId(), baby.babyName(), baby.birthDate(), baby.gender().name(),
                    imageUrlReader.apply(baby.profileImageUrl()), baby.myRole().name());
        }
    }

    public record TodaySummary(LocalDate date, long totalRecordCount, long feedingCount, long sleepCount,
                               long diaperCount, long temperatureCount, long medicineCount, long otherCount) {
        static TodaySummary from(LocalDate date, CareTypeCounts counts) {
            return new TodaySummary(date, counts.total(), counts.feeding(), counts.sleep(), counts.diaper(),
                    counts.temperature(), counts.medicine(), counts.other());
        }
    }
}
