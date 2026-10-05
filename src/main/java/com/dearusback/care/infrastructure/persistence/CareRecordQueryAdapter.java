package com.dearusback.care.infrastructure.persistence;

import com.dearusback.care.application.model.CareRecordView;
import com.dearusback.care.application.port.out.CareRecordQueryPort;
import com.dearusback.care.domain.model.CareRecord.BreastSide;
import com.dearusback.care.domain.model.CareRecord.CareRecordInputType;
import com.dearusback.care.domain.model.CareRecord.CareRecordStatus;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord.Detail;
import com.dearusback.care.domain.model.CareRecord.DiaperDetail;
import com.dearusback.care.domain.model.CareRecord.DiaperType;
import com.dearusback.care.domain.model.CareRecord.FeedingDetail;
import com.dearusback.care.domain.model.CareRecord.FeedingType;
import com.dearusback.care.domain.model.CareRecord.MedicineDetail;
import com.dearusback.care.domain.model.CareRecord.MedicineDoseUnit;
import com.dearusback.care.domain.model.CareRecord.OtherDetail;
import com.dearusback.care.domain.model.CareRecord.TemperatureDetail;
import com.dearusback.care.domain.model.CareRecord.TemperatureMeasuredPart;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CareRecordQueryAdapter implements CareRecordQueryPort {

    private static final String FIELDS = "record_id, baby_id, record_type, input_type, status, "
            + "recorded_at, start_at, end_at, memo, version";
    private static final String COMPLETED = " AND status = 'COMPLETED' AND is_deleted = FALSE";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public CareRecordQueryAdapter(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    @Override
    public List<CareRecordView> findCompletedRecords(Long babyId, Instant startInclusive, Instant endExclusive,
                                                     CareRecordType type) {
        String sql = "SELECT " + FIELDS + " FROM care_record WHERE baby_id = ?" + COMPLETED
                + " AND recorded_at >= ? AND recorded_at < ?"
                + (type == null ? "" : " AND record_type = ?")
                + " ORDER BY recorded_at DESC, record_id DESC";
        LocalDateTime start = utcTime(startInclusive);
        LocalDateTime end = utcTime(endExclusive);
        List<CareRecordView> rows = type == null
                ? jdbc.query(sql, (result, index) -> row(result), babyId, start, end)
                : jdbc.query(sql, (result, index) -> row(result), babyId, start, end, type.name());
        return attachDetails(rows);
    }

    @Override
    public List<Instant> findCompletedRecordedTimes(Long babyId, Instant startInclusive, Instant endExclusive) {
        return jdbc.query("SELECT recorded_at FROM care_record WHERE baby_id = ?" + COMPLETED
                        + " AND recorded_at >= ? AND recorded_at < ?",
                (result, index) -> instant(result, "recorded_at"), babyId,
                utcTime(startInclusive), utcTime(endExclusive));
    }

    @Override
    public Optional<CareRecordView> findCompletedRecord(Long babyId, Long recordId) {
        List<CareRecordView> rows = jdbc.query("SELECT " + FIELDS + " FROM care_record "
                        + "WHERE baby_id = ? AND record_id = ?" + COMPLETED,
                (result, index) -> row(result), babyId, recordId);
        return attachDetails(rows).stream().findFirst();
    }

    private List<CareRecordView> attachDetails(List<CareRecordView> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<CareRecordType, List<Long>> idsByType = new EnumMap<>(CareRecordType.class);
        for (CareRecordView row : rows) {
            if (row.recordType() != CareRecordType.SLEEP) {
                idsByType.computeIfAbsent(row.recordType(), ignored -> new ArrayList<>()).add(row.recordId());
            }
        }
        Map<Long, Detail> details = new HashMap<>();
        idsByType.forEach((type, ids) -> loadDetails(type, ids, details));
        return rows.stream().map(row -> {
            Detail detail = details.get(row.recordId());
            if (row.recordType() != CareRecordType.SLEEP && detail == null) {
                throw new IllegalStateException("Completed care record is missing its detail");
            }
            return row.withDetail(detail);
        }).toList();
    }

    private void loadDetails(CareRecordType type, List<Long> ids, Map<Long, Detail> details) {
        Map<String, Object> params = Map.of("ids", ids);
        switch (type) {
            case FEEDING -> named.query("SELECT record_id, feeding_type, amount_ml, breast_side, duration_minutes "
                    + "FROM feeding_detail WHERE record_id IN (:ids)", params, (RowCallbackHandler) result -> {
                FeedingType feedingType = FeedingType.valueOf(result.getString("feeding_type"));
                Integer amount = result.getObject("amount_ml", Integer.class);
                String breastSide = result.getString("breast_side");
                Integer duration = result.getObject("duration_minutes", Integer.class);
                details.put(result.getLong("record_id"), new FeedingDetail(feedingType, amount,
                        breastSide == null ? null : BreastSide.valueOf(breastSide), duration));
            });
            case DIAPER -> named.query("SELECT record_id, diaper_type, `condition`, color "
                    + "FROM diaper_detail WHERE record_id IN (:ids)", params, (RowCallbackHandler) result -> details.put(
                    result.getLong("record_id"), new DiaperDetail(DiaperType.valueOf(result.getString("diaper_type")),
                            result.getString("condition"), result.getString("color"))));
            case TEMPERATURE -> named.query("SELECT record_id, temperature_celsius, measured_part "
                    + "FROM temperature_detail WHERE record_id IN (:ids)", params, (RowCallbackHandler) result -> details.put(
                    result.getLong("record_id"), new TemperatureDetail(result.getBigDecimal("temperature_celsius"),
                            TemperatureMeasuredPart.valueOf(result.getString("measured_part")))));
            case MEDICINE -> named.query("SELECT record_id, medicine_name, dose_value, dose_unit "
                    + "FROM medicine_detail WHERE record_id IN (:ids)", params, (RowCallbackHandler) result -> details.put(
                    result.getLong("record_id"), new MedicineDetail(result.getString("medicine_name"),
                            result.getBigDecimal("dose_value"),
                            MedicineDoseUnit.valueOf(result.getString("dose_unit")))));
            case OTHER -> named.query("SELECT record_id, title FROM other_detail WHERE record_id IN (:ids)",
                    params, (RowCallbackHandler) result -> details.put(result.getLong("record_id"),
                            new OtherDetail(result.getString("title"))));
            case SLEEP -> { }
        }
    }

    private CareRecordView row(ResultSet result) throws SQLException {
        return new CareRecordView(result.getLong("record_id"), result.getLong("baby_id"),
                CareRecordType.valueOf(result.getString("record_type")),
                CareRecordInputType.valueOf(result.getString("input_type")),
                CareRecordStatus.valueOf(result.getString("status")),
                offset(result, "recorded_at"), offset(result, "start_at"), offset(result, "end_at"),
                result.getString("memo"), null, result.getLong("version"));
    }

    private OffsetDateTime offset(ResultSet result, String column) throws SQLException {
        Instant value = instant(result, column);
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        LocalDateTime value = result.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime utcTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
