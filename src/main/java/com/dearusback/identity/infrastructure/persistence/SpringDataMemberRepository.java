package com.dearusback.identity.infrastructure.persistence;

import com.dearusback.identity.domain.type.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMemberRepository extends JpaRepository<MemberJpaEntity, Long> {

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
