package com.dearusback.care.infrastructure.persistence;

import com.dearusback.care.domain.model.CareRecord;
import com.dearusback.care.domain.model.CareRecord.DiaperDetail;
import com.dearusback.care.domain.model.CareRecord.FeedingDetail;
import com.dearusback.care.domain.model.CareRecord.MedicineDetail;
import com.dearusback.care.domain.model.CareRecord.OtherDetail;
import com.dearusback.care.domain.model.CareRecord.TemperatureDetail;
import com.dearusback.care.domain.repository.CareRecordRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CareRecordPersistenceAdapter implements CareRecordRepository {

    private final SpringDataCareRecordRepository records;
    private final JdbcTemplate jdbc;

    public CareRecordPersistenceAdapter(SpringDataCareRecordRepository records, JdbcTemplate jdbc) {
        this.records = records;
        this.jdbc = jdbc;
    }

    @Override
    public CareRecord save(CareRecord record) {
        CareRecordJpaEntity saved = records.saveAndFlush(new CareRecordJpaEntity(record));
        long id = saved.id();
        switch (record.detail()) {
            case FeedingDetail detail -> jdbc.update("INSERT INTO feeding_detail "
                    + "(record_id, feeding_type, amount_ml, breast_side, duration_minutes) VALUES (?, ?, ?, ?, ?)",
                    id, detail.feedingType().name(), detail.amountMl(),
                    detail.breastSide() == null ? null : detail.breastSide().name(), detail.durationMinutes());
            case DiaperDetail detail -> jdbc.update("INSERT INTO diaper_detail "
                    + "(record_id, diaper_type, `condition`, color) VALUES (?, ?, ?, ?)",
                    id, detail.diaperType().name(), detail.condition(), detail.color());
            case TemperatureDetail detail -> jdbc.update("INSERT INTO temperature_detail "
                    + "(record_id, temperature_celsius, measured_part) VALUES (?, ?, ?)",
                    id, detail.temperatureCelsius(), detail.measuredPart().name());
            case MedicineDetail detail -> jdbc.update("INSERT INTO medicine_detail "
                    + "(record_id, medicine_name, dose_value, dose_unit) VALUES (?, ?, ?, ?)",
                    id, detail.medicineName(), detail.doseValue(), detail.doseUnit().name());
            case OtherDetail detail -> jdbc.update("INSERT INTO other_detail (record_id, title) VALUES (?, ?)",
                    id, detail.title());
            case null -> { }
        }
        return record.persisted(saved.id(), saved.version());
    }
}
