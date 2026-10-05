package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.application.model.MemberSummary;
import com.dearusback.family.application.port.out.MemberSummaryPort;
import com.dearusback.identity.application.port.in.ActiveMemberSummaryQuery;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class FamilyMemberSummaryAdapter implements MemberSummaryPort {

    private final ActiveMemberSummaryQuery members;

    public FamilyMemberSummaryAdapter(ActiveMemberSummaryQuery members) {
        this.members = members;
    }

    @Override
    public List<MemberSummary> findActiveSummaries(Set<Long> memberIds) {
        return members.findByIds(memberIds).stream()
                .map(member -> new MemberSummary(member.memberId(), member.memberName(), member.profileImageUrl()))
                .toList();
    }
}
