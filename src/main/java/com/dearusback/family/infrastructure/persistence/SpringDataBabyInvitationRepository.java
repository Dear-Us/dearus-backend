package com.dearusback.family.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataBabyInvitationRepository extends JpaRepository<BabyInvitationJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from BabyInvitationJpaEntity invitation where invitation.babyId = :babyId "
            + "order by invitation.createdAt desc, invitation.id desc")
    List<BabyInvitationJpaEntity> findAllByBabyIdForUpdate(@Param("babyId") Long babyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from BabyInvitationJpaEntity invitation "
            + "where invitation.id = :invitationId and invitation.babyId = :babyId")
    Optional<BabyInvitationJpaEntity> findByIdAndBabyIdForUpdate(
            @Param("invitationId") Long invitationId, @Param("babyId") Long babyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from BabyInvitationJpaEntity invitation where invitation.id = :invitationId")
    Optional<BabyInvitationJpaEntity> findByIdForUpdate(@Param("invitationId") Long invitationId);

    Optional<BabyInvitationJpaEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from BabyInvitationJpaEntity invitation where invitation.tokenHash = :tokenHash")
    Optional<BabyInvitationJpaEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Query("select invitation.babyId from BabyInvitationJpaEntity invitation "
            + "where invitation.tokenHash = :tokenHash")
    Optional<Long> findBabyIdByTokenHash(@Param("tokenHash") String tokenHash);
}
