package com.dearusback.growth.domain.repository;

import com.dearusback.growth.domain.model.GrowthRecord;
import java.util.List;
import java.util.Optional;

public interface GrowthRecordRepository {
    GrowthRecord save(GrowthRecord record);

    GrowthRecord update(GrowthRecord record);

    List<GrowthRecord> findAllByBaby(Long babyId);

    Optional<GrowthRecord> findByBabyAndId(Long babyId, Long growthRecordId);
}
