package com.dearusback.family.application.service;

import com.dearusback.family.application.model.BabyMemberSummary;
import com.dearusback.family.application.model.BabyMembershipView;
import com.dearusback.family.application.model.MemberSummary;
import com.dearusback.family.application.port.in.GetBabyMembersUseCase;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.family.application.port.out.MemberSummaryPort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetBabyMembersService implements GetBabyMembersUseCase {

    private static final Comparator<BabyMembershipView> ORDER =
            Comparator.comparing((BabyMembershipView membership) -> membership.role() != BabyMemberRole.OWNER)
                    .thenComparing(BabyMembershipView::joinedAt)
                    .thenComparing(BabyMembershipView::memberId);

    private final BabyRegistrationMemberAccess members;
    private final BabyQueryPort babies;
    private final MemberSummaryPort summaries;

    public GetBabyMembersService(BabyRegistrationMemberAccess members, BabyQueryPort babies,
                                 MemberSummaryPort summaries) {
        this.members = members;
        this.babies = babies;
        this.summaries = summaries;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BabyMemberSummary> getMembers(Long memberId, Long babyId) {
        members.requireActiveMember(memberId);
        babies.findAccessibleBaby(memberId, babyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        List<BabyMembershipView> active = babies.findActiveMembers(babyId).stream().sorted(ORDER).toList();
        Set<Long> ids = active.stream().map(BabyMembershipView::memberId).collect(Collectors.toSet());
        Map<Long, MemberSummary> byId = summaries.findActiveSummaries(ids).stream()
                .collect(Collectors.toMap(MemberSummary::memberId, Function.identity()));
        if (byId.size() != ids.size() || !byId.keySet().containsAll(ids)) {
            throw new IllegalStateException("Active baby membership references a missing or withdrawn member");
        }
        return active.stream().map(membership -> {
            MemberSummary summary = byId.get(membership.memberId());
            return new BabyMemberSummary(summary.memberId(), summary.memberName(),
                    summary.profileImageUrl(), membership.role());
        }).toList();
    }
}
