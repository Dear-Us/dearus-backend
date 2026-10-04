package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.identity.application.port.out.AccessibleBabyPort;
import org.springframework.stereotype.Repository;

@Repository
public class FamilyAccessibleBabyAdapter implements AccessibleBabyPort {

    private final BabyQueryPort babies;

    public FamilyAccessibleBabyAdapter(BabyQueryPort babies) {
        this.babies = babies;
    }

    @Override
    public boolean existsActiveMembership(Long memberId, Long babyId) {
        return babies.findAccessibleBaby(memberId, babyId).isPresent();
    }
}
