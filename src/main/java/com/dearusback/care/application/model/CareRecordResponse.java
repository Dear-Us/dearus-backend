package com.dearusback.care.application.model;

import com.dearusback.care.domain.model.CareRecord;
import com.dearusback.care.domain.model.CareRecord.Detail;
import com.dearusback.care.domain.model.CareRecord.BreastSide;
import com.dearusback.care.domain.model.CareRecord.CareRecordInputType;
import com.dearusback.care.domain.model.CareRecord.CareRecordStatus;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord.DiaperDetail;
import com.dearusback.care.domain.model.CareRecord.DiaperType;
import com.dearusback.care.domain.model.CareRecord.FeedingDetail;
import com.dearusback.care.domain.model.CareRecord.FeedingType;
import com.dearusback.care.domain.model.CareRecord.MedicineDetail;
import com.dearusback.care.domain.model.CareRecord.MedicineDoseUnit;
import com.dearusback.care.domain.model.CareRecord.OtherDetail;
import com.dearusback.care.domain.model.CareRecord.TemperatureDetail;
import com.dearusback.care.domain.model.CareRecord.TemperatureMeasuredPart;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CareRecordResponse(Long recordId, Long babyId, CareRecordType recordType,
                                 CareRecordInputType inputType, CareRecordStatus status,
                                 OffsetDateTime recordedAt, OffsetDateTime startAt, OffsetDateTime endAt,
                                 String memo, Object detail, long version) {

    public static CareRecordResponse from(CareRecord record) {
        return new CareRecordResponse(record.id(), record.babyId(), record.recordType(), record.inputType(),
                record.status(), record.recordedAt(), record.startAt(), record.endAt(), record.memo(),
                detail(record.detail()), record.version());
    }

    public static CareRecordResponse from(CareRecordView record) {
        return new CareRecordResponse(record.recordId(), record.babyId(), record.recordType(), record.inputType(),
                record.status(), record.recordedAt(), record.startAt(), record.endAt(), record.memo(),
                detail(record.detail()), record.version());
    }

    private static Object detail(Detail raw) {
        return switch (raw) {
            case FeedingDetail feeding when feeding.feedingType() == FeedingType.FORMULA ->
                    new FormulaResponse(feeding.feedingType(), feeding.amountMl());
            case FeedingDetail feeding -> new BreastfeedingResponse(feeding.feedingType(), feeding.breastSide(),
                    feeding.durationMinutes());
            case DiaperDetail diaper -> new DiaperResponse(diaper.diaperType(), diaper.condition(), diaper.color());
            case TemperatureDetail temperature -> new TemperatureResponse(temperature.temperatureCelsius(),
                    temperature.measuredPart());
            case MedicineDetail medicine -> new MedicineResponse(medicine.medicineName(), medicine.doseValue(),
                    medicine.doseUnit());
            case OtherDetail other -> new OtherResponse(other.title());
            case null -> null;
        };
    }

    public record FormulaResponse(FeedingType feedingType, int amountMl) { }
    public record BreastfeedingResponse(FeedingType feedingType, BreastSide breastSide, Integer durationMinutes) { }
    public record DiaperResponse(DiaperType diaperType, String condition, String color) { }
    public record TemperatureResponse(BigDecimal temperatureCelsius, TemperatureMeasuredPart measuredPart) { }
    public record MedicineResponse(String medicineName, BigDecimal doseValue, MedicineDoseUnit doseUnit) { }
    public record OtherResponse(String title) { }
}
