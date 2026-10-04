package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.domain.model.BabyMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataBabyMemberRepository extends JpaRepository<BabyMemberJpaEntity, Long> {

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
