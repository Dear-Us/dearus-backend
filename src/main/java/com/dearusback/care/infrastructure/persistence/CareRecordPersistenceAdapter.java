package com.dearusback.care.infrastructure.persistence;

import com.dearusback.care.domain.model.CareRecord;
import com.dearusback.care.domain.model.CareRecord.DiaperDetail;
import com.dearusback.care.domain.model.CareRecord.FeedingDetail;
import com.dearusback.care.domain.model.CareRecord.BreastSide;
import com.dearusback.care.domain.model.CareRecord.CareRecordInputType;
import com.dearusback.care.domain.model.CareRecord.CareRecordStatus;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord.FeedingType;
import com.dearusback.care.domain.model.CareRecord.MedicineDetail;
import com.dearusback.care.domain.model.CareRecord.OtherDetail;
import com.dearusback.care.domain.model.CareRecord.TemperatureDetail;
import com.dearusback.care.domain.repository.CareRecordRepository;
import com.dearusback.care.application.port.out.CareRecordQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class CareRecordPersistenceAdapter implements CareRecordRepository {

    private final SpringDataCareRecordRepository records;
    private final JdbcTemplate jdbc;
    private final CareRecordQueryPort queries;

    public CareRecordPersistenceAdapter(SpringDataCareRecordRepository records, JdbcTemplate jdbc,
                                        CareRecordQueryPort queries) {
        this.records = records;
        this.jdbc = jdbc;
        this.queries = queries;
    }

    @Override
    public CareRecord save(CareRecord record) {
        CareRecordJpaEntity saved = records.saveAndFlush(new CareRecordJpaEntity(record));
        long id = saved.id();
        insertDetail(id, record.detail());
        return record.persisted(saved.id(), saved.version());
    }

    @Override
    public Optional<CareRecord> findByBabyAndId(Long babyId, Long recordId) {
        return records.findByIdAndBabyIdAndDeletedFalse(recordId, babyId).map(entity -> {
            CareRecord.Detail detail = entity.status() == CareRecordStatus.COMPLETED
                    ? queries.findCompletedRecord(babyId, recordId)
                            .orElseThrow(() -> new IllegalStateException("Completed record disappeared"))
                            .detail()
                    : null;
            return entity.toDomain(detail);
        });
    }

    @Override
    public CareRecord updateCompleted(CareRecord record, boolean replaceDetail) {
        CareRecordJpaEntity entity = records.findByIdAndBabyIdAndDeletedFalse(record.id(), record.babyId())
                .orElseThrow(() -> new OptimisticLockingFailureException("Care record was deleted"));
        if (entity.version() != record.version()) {
            throw new OptimisticLockingFailureException("Care record version changed");
        }
        entity.apply(record);
        CareRecordJpaEntity saved = records.saveAndFlush(entity);
        if (replaceDetail && !record.deleted() && record.recordType() != CareRecordType.SLEEP) {
            deleteDetail(record.id(), record.recordType());
            insertDetail(record.id(), record.detail());
        }
        return record.persisted(saved.id(), saved.version());
    }

    private void deleteDetail(Long id, CareRecordType type) {
        String table = switch (type) {
            case FEEDING -> "feeding_detail";
            case DIAPER -> "diaper_detail";
            case TEMPERATURE -> "temperature_detail";
            case MEDICINE -> "medicine_detail";
            case OTHER -> "other_detail";
            case SLEEP -> throw new IllegalArgumentException("Sleep has no detail");
        };
        if (jdbc.update("DELETE FROM " + table + " WHERE record_id = ?", id) != 1) {
            throw new IllegalStateException("Completed care detail is missing");
        }
    }

    private void insertDetail(long id, CareRecord.Detail raw) {
        switch (raw) {
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
    }

    @Override
    public Optional<CareRecord> findByBabyAndIdForUpdate(Long babyId, Long recordId) {
        List<CareRecord> found = jdbc.query("SELECT record_id, baby_id, record_type, input_type, status, "
                        + "recorded_at, start_at, end_at, memo, created_by_member_id, completed_by_member_id, "
                        + "last_updated_by_member_id, deleted_by_member_id, version, is_deleted, created_at, "
                        + "updated_at, deleted_at FROM care_record "
                        + "WHERE baby_id = ? AND record_id = ? AND is_deleted = FALSE FOR UPDATE",
                (result, index) -> lockedRecord(result), babyId, recordId);
        return found.stream().findFirst();
    }

    @Override
    public CareRecord updateTransition(CareRecord record) {
        int updated = jdbc.update("UPDATE care_record SET status = ?, end_at = ?, completed_by_member_id = ?, "
                        + "last_updated_by_member_id = ?, updated_at = ?, version = version + 1 "
                        + "WHERE record_id = ? AND baby_id = ? AND status = 'IN_PROGRESS' "
                        + "AND is_deleted = FALSE AND version = ?",
                record.status().name(), utc(record.endAt()), record.completedByMemberId(),
                record.lastUpdatedByMemberId(), LocalDateTime.ofInstant(record.updatedAt(), ZoneOffset.UTC),
                record.id(), record.babyId(), record.version());
        if (updated != 1) {
            throw new IllegalStateException("Care record transition lost its lock or version");
        }
        if (record.status() == CareRecordStatus.COMPLETED && record.detail() instanceof FeedingDetail feeding) {
            int detailUpdated = jdbc.update("UPDATE feeding_detail SET duration_minutes = ? WHERE record_id = ?",
                    feeding.durationMinutes(), record.id());
            if (detailUpdated != 1) {
                throw new IllegalStateException("Active feeding detail is missing");
            }
        }
        return record.persisted(record.id(), record.version() + 1);
    }

    private CareRecord lockedRecord(ResultSet result) throws SQLException {
        long id = result.getLong("record_id");
        CareRecordType type = CareRecordType.valueOf(result.getString("record_type"));
        CareRecord.Detail detail = null;
        if (type == CareRecordType.FEEDING) {
            List<FeedingDetail> details = jdbc.query("SELECT feeding_type, amount_ml, breast_side, duration_minutes "
                            + "FROM feeding_detail WHERE record_id = ?", (row, index) -> {
                String side = row.getString("breast_side");
                return new FeedingDetail(FeedingType.valueOf(row.getString("feeding_type")),
                        row.getObject("amount_ml", Integer.class),
                        side == null ? null : BreastSide.valueOf(side),
                        row.getObject("duration_minutes", Integer.class));
            }, id);
            if (details.size() != 1) {
                throw new IllegalStateException("Care feeding detail is missing");
            }
            detail = details.getFirst();
        }
        return new CareRecord(id, result.getLong("baby_id"), type,
                CareRecordInputType.valueOf(result.getString("input_type")),
                CareRecordStatus.valueOf(result.getString("status")),
                offset(result, "recorded_at"), offset(result, "start_at"), offset(result, "end_at"),
                result.getString("memo"), detail, result.getLong("created_by_member_id"),
                result.getObject("completed_by_member_id", Long.class),
                result.getLong("last_updated_by_member_id"),
                result.getObject("deleted_by_member_id", Long.class), result.getLong("version"),
                result.getBoolean("is_deleted"), instant(result, "created_at"),
                instant(result, "updated_at"), instant(result, "deleted_at"));
    }

    private OffsetDateTime offset(ResultSet result, String field) throws SQLException {
        Instant value = instant(result, field);
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(ResultSet result, String field) throws SQLException {
        LocalDateTime value = result.getObject(field, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime utc(OffsetDateTime value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }
}
