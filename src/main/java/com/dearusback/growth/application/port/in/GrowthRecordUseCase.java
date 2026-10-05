package com.dearusback.growth.application.port.in;

import com.dearusback.growth.application.model.CreateGrowthRecordCommand;
import com.dearusback.growth.application.model.UpdateGrowthRecordCommand;
import com.dearusback.growth.domain.model.GrowthRecord;
import java.util.List;

public interface GrowthRecordUseCase {
    List<GrowthRecord> list(Long memberId, Long babyId);

    GrowthRecord create(CreateGrowthRecordCommand command);

    GrowthRecord detail(Long memberId, Long babyId, Long growthRecordId);

    GrowthRecord update(UpdateGrowthRecordCommand command);

    void delete(Long memberId, Long babyId, Long growthRecordId, long version);
}
