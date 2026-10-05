package com.dearusback.family.application.port.out;

import com.dearusback.family.application.model.MemberSummary;
import java.util.List;
import java.util.Set;

public interface MemberSummaryPort {
    List<MemberSummary> findActiveSummaries(Set<Long> memberIds);
}
