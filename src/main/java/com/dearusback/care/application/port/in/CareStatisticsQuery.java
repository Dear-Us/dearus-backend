package com.dearusback.care.application.port.in;

import com.dearusback.care.application.model.CareStatisticsSource;
import java.time.Instant;

public interface CareStatisticsQuery {
    CareStatisticsSource load(Long babyId, Instant startInclusive, Instant endExclusive);
}
