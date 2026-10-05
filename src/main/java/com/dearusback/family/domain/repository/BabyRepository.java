package com.dearusback.family.domain.repository;

import com.dearusback.family.domain.model.Baby;
import java.util.Optional;

public interface BabyRepository {
    Baby save(Baby baby);

    Optional<Baby> findByIdForUpdate(Long babyId, Long memberId);

    Optional<Baby> findByIdAndAllMembersForUpdate(Long babyId);

    Baby update(Baby baby);

    void markDeleted(Baby baby);

    void saveCaregiverMembership(Baby baby, Long memberId);
}
