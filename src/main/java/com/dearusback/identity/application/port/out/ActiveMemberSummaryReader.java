package com.dearusback.identity.application.port.out;

import com.dearusback.identity.application.model.ActiveMemberSummary;
import java.util.List;
import java.util.Set;

public interface ActiveMemberSummaryReader {
    List<ActiveMemberSummary> findByIds(Set<Long> memberIds);
}
