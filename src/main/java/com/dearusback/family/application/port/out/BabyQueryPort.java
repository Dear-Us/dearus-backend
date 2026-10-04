package com.dearusback.family.application.port.out;

import com.dearusback.family.application.model.MyBabySummary;
import java.util.List;
import java.util.Optional;

public interface BabyQueryPort {
    List<MyBabySummary> findActiveBabiesByMemberId(Long memberId);

    Optional<MyBabySummary> findAccessibleBaby(Long memberId, Long babyId);
}
