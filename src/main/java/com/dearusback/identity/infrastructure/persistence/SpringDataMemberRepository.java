package com.dearusback.identity.infrastructure.persistence;

import com.dearusback.identity.domain.type.SocialProvider;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMemberRepository extends JpaRepository<MemberJpaEntity, Long> {

    @Modifying
    @Query("update MemberJpaEntity member set member.lastSelectedBabyId = :babyId "
            + "where member.id = :memberId and member.lastSelectedBabyId is null and member.deleted = false")
    int initializeLastSelectedBabyId(@Param("memberId") Long memberId, @Param("babyId") Long babyId);

    @Modifying
    @Query("update MemberJpaEntity member set member.lastSelectedBabyId = null "
            + "where member.lastSelectedBabyId = :babyId")
    int clearLastSelectedBabyIfMatches(@Param("babyId") Long babyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from MemberJpaEntity member where member.id = :memberId")
    Optional<MemberJpaEntity> findByIdForUpdate(@Param("memberId") Long memberId);

    @Query("select distinct member from MemberJpaEntity member left join fetch member.socialAccounts where member.id = :memberId")
    Optional<MemberJpaEntity> findWithSocialAccountsById(@Param("memberId") Long memberId);

    @Query("""
            select distinct member
            from MemberJpaEntity member
            left join fetch member.socialAccounts accounts
            where exists (
                select matchingAccount.id
                from SocialAccountJpaEntity matchingAccount
                where matchingAccount.member = member
                  and matchingAccount.provider = :provider
                  and matchingAccount.providerUserId = :providerUserId
            )
            """)
    Optional<MemberJpaEntity> findBySocialAccount(
            @Param("provider") SocialProvider provider,
            @Param("providerUserId") String providerUserId
    );
}
