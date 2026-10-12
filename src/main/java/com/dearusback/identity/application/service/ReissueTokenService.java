package com.dearusback.identity.application.service;

import com.dearusback.identity.application.model.AuthTokenPolicy;
import com.dearusback.identity.application.model.IssuedAccessToken;
import com.dearusback.identity.application.model.ReissueTokenCommand;
import com.dearusback.identity.application.model.ReissueTokenResult;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.port.in.ReissueTokenUseCase;
import com.dearusback.identity.application.port.out.AccessTokenIssuer;
import com.dearusback.identity.application.port.out.RefreshSessionRepository;
import com.dearusback.identity.application.port.out.RefreshTokenGenerator;
import com.dearusback.identity.application.port.out.RefreshTokenHasher;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.model.RefreshSession;
import com.dearusback.identity.domain.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReissueTokenService implements ReissueTokenUseCase {

    private final RefreshSessionRepository sessions;
    private final MemberRepository members;
    private final RefreshTokenHasher hasher;
    private final RefreshTokenGenerator generator;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AuthTokenPolicy tokenPolicy;
    private final Clock clock;

    public ReissueTokenService(
            RefreshSessionRepository sessions,
            MemberRepository members,
            RefreshTokenHasher hasher,
            RefreshTokenGenerator generator,
            AccessTokenIssuer accessTokenIssuer,
            AuthTokenPolicy tokenPolicy,
            Clock clock
    ) {
        this.sessions = sessions;
        this.members = members;
        this.hasher = hasher;
        this.generator = generator;
        this.accessTokenIssuer = accessTokenIssuer;
        this.tokenPolicy = tokenPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReissueTokenResult reissue(ReissueTokenCommand command) {
        Instant now = clock.instant();
        RefreshSession oldSession = sessions.findByTokenHash(hasher.hash(command.refreshToken()))
                .orElseThrow(this::invalidRefreshToken);

        if (!oldSession.canRotateAt(now)) {
            throw invalidRefreshToken();
        }

        Member member = members.findByIdForUpdate(oldSession.memberId())
                .orElseThrow(this::invalidRefreshToken);
        if (member.isDeleted()) {
            throw new IdentityException(IdentityErrorCode.WITHDRAWN_MEMBER);
        }

        if (!sessions.revokeIfActive(oldSession.id(), now)) {
            throw invalidRefreshToken();
        }

        String newRefreshToken = generator.generate();
        Instant expiresAt = now.plus(tokenPolicy.refreshTokenTtl());
        sessions.save(member.id(), hasher.hash(newRefreshToken), expiresAt, now);
        IssuedAccessToken accessToken = accessTokenIssuer.issue(member.id(), now);

        return new ReissueTokenResult(
                accessToken.value(),
                newRefreshToken,
                SocialLoginResult.BEARER,
                accessToken.expiresInSeconds(),
                tokenPolicy.refreshTokenTtl().toSeconds()
        );
    }

    private IdentityException invalidRefreshToken() {
        return new IdentityException(IdentityErrorCode.INVALID_REFRESH_TOKEN);
    }
}
