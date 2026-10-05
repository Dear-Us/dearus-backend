package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.application.model.InvitationBabyView;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataBabyRepository extends JpaRepository<BabyJpaEntity, Long> {

    @Query("select new com.dearusback.family.application.model.InvitationBabyView(baby.name, "
            + "baby.profileImageUrl) from BabyJpaEntity baby "
            + "where baby.id = :babyId and baby.deleted = false")
    Optional<InvitationBabyView> findActiveInvitationBaby(@Param("babyId") Long babyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select baby from BabyJpaEntity baby where baby.id = :babyId and baby.deleted = false")
    Optional<BabyJpaEntity> findByIdForUpdate(@Param("babyId") Long babyId);
}
