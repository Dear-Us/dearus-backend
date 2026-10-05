package com.dearusback.identity.infrastructure.persistence;

import com.dearusback.identity.application.model.ActiveMemberSummary;
import com.dearusback.identity.application.port.out.ActiveMemberSummaryReader;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class ActiveMemberSummaryAdapter implements ActiveMemberSummaryReader {

    private final SpringDataMemberRepository repository;

    public ActiveMemberSummaryAdapter(SpringDataMemberRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ActiveMemberSummary> findByIds(Set<Long> memberIds) {
        return repository.findActiveSummaries(memberIds);
    }
}
