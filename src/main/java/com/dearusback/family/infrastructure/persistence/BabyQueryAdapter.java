package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.model.BabyMembershipView;
import com.dearusback.family.application.model.InvitationBabyView;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.family.domain.model.BabyMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class BabyQueryAdapter implements BabyQueryPort {

    private final SpringDataBabyMemberRepository repository;
    private final SpringDataBabyRepository babies;

    public BabyQueryAdapter(SpringDataBabyMemberRepository repository, SpringDataBabyRepository babies) {
        this.repository = repository;
        this.babies = babies;
    }

    @Override
    public List<MyBabySummary> findActiveBabiesByMemberId(Long memberId) {
        return repository.findActiveBabiesByMemberId(memberId, BabyMemberStatus.ACTIVE);
    }

    @Override
    public Optional<MyBabySummary> findAccessibleBaby(Long memberId, Long babyId) {
        return repository.findAccessibleBaby(memberId, babyId, BabyMemberStatus.ACTIVE);
    }

    @Override
    public List<BabyMembershipView> findActiveMembers(Long babyId) {
        return repository.findActiveMembers(babyId, BabyMemberStatus.ACTIVE);
    }

    @Override
    public Optional<InvitationBabyView> findActiveInvitationBaby(Long babyId) {
        return babies.findActiveInvitationBaby(babyId);
    }
}
