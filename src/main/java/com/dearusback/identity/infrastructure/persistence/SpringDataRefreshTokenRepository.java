package com.dearusback.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update RefreshTokenJpaEntity token
               set token.revokedAt = :now
             where token.id = :sessionId
               and token.revokedAt is null
               and token.expiresAt > :now
            """)
    int revokeIfActive(@Param("sessionId") Long sessionId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update RefreshTokenJpaEntity token
               set token.revokedAt = :revokedAt
             where token.tokenHash = :tokenHash
               and token.revokedAt is null
            """)
    int revokeByTokenHash(@Param("tokenHash") String tokenHash, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("update RefreshTokenJpaEntity token set token.revokedAt = :revokedAt "
            + "where token.memberId = :memberId and token.revokedAt is null")
    int revokeAllForMember(@Param("memberId") Long memberId, @Param("revokedAt") Instant revokedAt);
}
