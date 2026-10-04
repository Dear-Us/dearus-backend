package com.dearusback.family.application.port.out;

import com.dearusback.family.application.model.MyBabySummary;
import java.util.List;

public interface BabyQueryPort {
    List<MyBabySummary> findActiveBabiesByMemberId(Long memberId);
}
