package com.dearusback.identity.application.service;

import com.dearusback.identity.application.model.ActiveMemberSummary;
import com.dearusback.identity.application.port.in.ActiveMemberSummaryQuery;
import com.dearusback.identity.application.port.out.ActiveMemberSummaryReader;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActiveMemberSummaryService implements ActiveMemberSummaryQuery {

    private final ActiveMemberSummaryReader reader;

    public ActiveMemberSummaryService(ActiveMemberSummaryReader reader) {
        this.reader = reader;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveMemberSummary> findByIds(Set<Long> memberIds) {
        return memberIds.isEmpty() ? List.of() : reader.findByIds(memberIds);
    }
}
