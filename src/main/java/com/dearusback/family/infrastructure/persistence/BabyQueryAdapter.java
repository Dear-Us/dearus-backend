package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.family.domain.model.BabyMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class BabyQueryAdapter implements BabyQueryPort {

    private final SpringDataBabyMemberRepository repository;

    public BabyQueryAdapter(SpringDataBabyMemberRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<MyBabySummary> findActiveBabiesByMemberId(Long memberId) {
        return repository.findActiveBabiesByMemberId(memberId, BabyMemberStatus.ACTIVE);
    }

    @Override
    public Optional<MyBabySummary> findAccessibleBaby(Long memberId, Long babyId) {
        return repository.findAccessibleBaby(memberId, babyId, BabyMemberStatus.ACTIVE);
    }
}
