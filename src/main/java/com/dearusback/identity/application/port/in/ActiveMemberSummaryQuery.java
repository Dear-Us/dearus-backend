package com.dearusback.identity.application.port.in;

import com.dearusback.identity.application.model.ActiveMemberSummary;
import java.util.List;
import java.util.Set;

public interface ActiveMemberSummaryQuery {
    List<ActiveMemberSummary> findByIds(Set<Long> memberIds);
}
