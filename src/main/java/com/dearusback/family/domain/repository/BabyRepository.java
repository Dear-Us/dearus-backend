package com.dearusback.family.domain.repository;

import com.dearusback.family.domain.model.Baby;
import java.util.Optional;

public interface BabyRepository {
    Baby save(Baby baby);

    Optional<Baby> findByIdForUpdate(Long babyId, Long memberId);

    Baby update(Baby baby);
}
