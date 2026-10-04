package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.domain.model.BabyMemberStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataBabyMemberRepository extends JpaRepository<BabyMemberJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from BabyMemberJpaEntity membership "
            + "where membership.baby.id = :babyId and membership.memberId = :memberId")
    Optional<BabyMemberJpaEntity> findByBabyAndMemberForUpdate(
            @Param("babyId") Long babyId, @Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from BabyMemberJpaEntity membership "
            + "where membership.baby.id = :babyId order by membership.id")
    List<BabyMemberJpaEntity> findAllByBabyIdForUpdate(@Param("babyId") Long babyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from BabyMemberJpaEntity membership join membership.baby baby "
            + "where membership.memberId = :memberId and membership.status = :activeStatus "
            + "and baby.deleted = false order by membership.id")
    List<BabyMemberJpaEntity> findActiveMembershipsByMemberForUpdate(
            @Param("memberId") Long memberId, @Param("activeStatus") BabyMemberStatus activeStatus);

    @Query("""
            select new com.dearusback.family.application.model.MyBabySummary(
                baby.id, baby.name, baby.birthDate, baby.gender, baby.profileImageUrl, membership.role)
            from BabyMemberJpaEntity membership
            join membership.baby baby
            where membership.memberId = :memberId
              and membership.status = :activeStatus
              and baby.deleted = false
            order by baby.createdAt asc, baby.id asc
            """)
    List<MyBabySummary> findActiveBabiesByMemberId(
            @Param("memberId") Long memberId, @Param("activeStatus") BabyMemberStatus activeStatus);

    @Query("""
            select new com.dearusback.family.application.model.MyBabySummary(
                baby.id, baby.name, baby.birthDate, baby.gender, baby.profileImageUrl, membership.role)
            from BabyMemberJpaEntity membership
            join membership.baby baby
            where baby.id = :babyId
              and baby.deleted = false
              and membership.memberId = :memberId
              and membership.status = :activeStatus
            """)
    Optional<MyBabySummary> findAccessibleBaby(
            @Param("memberId") Long memberId, @Param("babyId") Long babyId,
            @Param("activeStatus") BabyMemberStatus activeStatus);
}
