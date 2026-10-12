package com.dearusback.growth.infrastructure.persistence;

import com.dearusback.growth.application.model.GrowthMeasurement;
import com.dearusback.growth.application.port.in.GrowthMeasurementsQuery;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GrowthMeasurementsQueryAdapter implements GrowthMeasurementsQuery {
    private final JdbcTemplate jdbc;

    public GrowthMeasurementsQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<GrowthMeasurement> findAll(Long babyId) {
        return jdbc.query("SELECT growth_record_id, measured_date, height_cm, weight_kg "
                        + "FROM growth_record WHERE baby_id = ? AND is_deleted = FALSE "
                        + "ORDER BY measured_date ASC, growth_record_id ASC",
                (row, index) -> new GrowthMeasurement(row.getLong("growth_record_id"),
                        row.getDate("measured_date").toLocalDate(), row.getBigDecimal("height_cm"),
                        row.getBigDecimal("weight_kg")), babyId);
    }
}
