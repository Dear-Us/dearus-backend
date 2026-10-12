package com.dearusback.family.domain.repository;

import com.dearusback.family.domain.model.BabyInvitation;
import java.util.List;
import java.util.Optional;

public interface BabyInvitationRepository {
    BabyInvitation save(BabyInvitation invitation);

    List<BabyInvitation> findAllByBabyIdForUpdate(Long babyId);

    Optional<BabyInvitation> findByIdAndBabyIdForUpdate(Long invitationId, Long babyId);

    Optional<BabyInvitation> findByIdForUpdate(Long invitationId);

    Optional<BabyInvitation> findByTokenHash(String tokenHash);

    Optional<BabyInvitation> findByTokenHashForUpdate(String tokenHash);

    Optional<Long> findBabyIdByTokenHash(String tokenHash);
}
