package com.dearusback.care.infrastructure.persistence;

import com.dearusback.care.application.model.CareStatisticsSource;
import com.dearusback.care.application.model.CareStatisticsSource.Kind;
import com.dearusback.care.application.model.CareStatisticsSource.Measurement;
import com.dearusback.care.application.model.CareStatisticsSource.SleepInterval;
import com.dearusback.care.application.port.in.CareStatisticsQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CareStatisticsQueryAdapter implements CareStatisticsQuery {
    private final JdbcTemplate jdbc;

    public CareStatisticsQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CareStatisticsSource load(Long babyId, Instant startInclusive, Instant endExclusive) {
        LocalDateTime start = utc(startInclusive);
        LocalDateTime end = utc(endExclusive);
        List<Measurement> measurements = jdbc.query("SELECT care.record_id, care.recorded_at, care.record_type, "
                        + "feeding.feeding_type, feeding.amount_ml, feeding.duration_minutes, "
                        + "diaper.diaper_type, temperature.temperature_celsius "
                        + "FROM care_record care "
                        + "LEFT JOIN feeding_detail feeding ON feeding.record_id = care.record_id "
                        + "LEFT JOIN diaper_detail diaper ON diaper.record_id = care.record_id "
                        + "LEFT JOIN temperature_detail temperature ON temperature.record_id = care.record_id "
                        + "WHERE care.baby_id = ? AND care.status = 'COMPLETED' AND care.is_deleted = FALSE "
                        + "AND care.record_type <> 'SLEEP' AND care.recorded_at >= ? AND care.recorded_at < ?",
                (row, index) -> measurement(row), babyId, start, end);
        List<SleepInterval> sleeps = jdbc.query("SELECT start_at, end_at FROM care_record "
                        + "WHERE baby_id = ? AND record_type = 'SLEEP' AND status = 'COMPLETED' "
                        + "AND is_deleted = FALSE AND start_at < ? AND end_at > ?",
                (row, index) -> new SleepInterval(instant(row, "start_at"), instant(row, "end_at")),
                babyId, end, start);
        return new CareStatisticsSource(measurements, sleeps);
    }

    private Measurement measurement(ResultSet row) throws SQLException {
        Kind kind = switch (row.getString("record_type")) {
            case "FEEDING" -> Kind.valueOf(row.getString("feeding_type"));
            case "DIAPER" -> Kind.valueOf("DIAPER_" + row.getString("diaper_type"));
            case "TEMPERATURE" -> Kind.TEMPERATURE;
            case "MEDICINE" -> Kind.MEDICINE;
            case "OTHER" -> Kind.OTHER;
            default -> throw new IllegalStateException("Unsupported care statistics type");
        };
        return new Measurement(row.getLong("record_id"), instant(row, "recorded_at"), kind,
                row.getObject("amount_ml", Integer.class), row.getObject("duration_minutes", Integer.class),
                row.getBigDecimal("temperature_celsius"));
    }

    private Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
