package com.dearusback.growth.application.port.in;

import com.dearusback.growth.application.model.GrowthMeasurement;
import java.util.List;

public interface GrowthMeasurementsQuery {
    List<GrowthMeasurement> findAll(Long babyId);
}
