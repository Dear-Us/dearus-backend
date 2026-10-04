package com.dearusback.identity.application.service;

import com.dearusback.identity.application.model.AuthTokenPolicy;
import com.dearusback.identity.application.model.IssuedAccessToken;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.model.SocialLoginResult.MemberResult;
import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.application.port.out.AccessTokenIssuer;
import com.dearusback.identity.application.port.out.RefreshSessionRepository;
import com.dearusback.identity.application.port.out.RefreshTokenGenerator;
import com.dearusback.identity.application.port.out.RefreshTokenHasher;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.domain.exception.SocialAccountConflictException;
import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.model.SocialAccount;
import com.dearusback.identity.domain.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialLoginTransactionService {

    private final MemberRepository memberRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthTokenPolicy tokenPolicy;
    private final Clock clock;

    public SocialLoginTransactionService(
            MemberRepository memberRepository,
            RefreshSessionRepository refreshSessionRepository,
            AccessTokenIssuer accessTokenIssuer,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHasher refreshTokenHasher,
            AuthTokenPolicy tokenPolicy,
            Clock clock
    ) {
        this.memberRepository = memberRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenHasher = refreshTokenHasher;
        this.tokenPolicy = tokenPolicy;
        this.clock = clock;
    }

    @Transactional
    public SocialLoginResult loginOrRegister(VerifiedSocialIdentity identity) {
        Member member = memberRepository
                .findBySocialAccount(identity.provider(), identity.providerUserId())
                .orElse(null);

        if (member != null) {
            return loginExisting(member, identity);
        }

        Member newMember = Member.register(
                identity.displayName(),
                identity.email(),
                identity.profileImageUrl(),
                SocialAccount.create(identity.provider(), identity.providerUserId(), identity.email())
        );

        Member savedMember = memberRepository.save(newMember);
        return issueSession(savedMember, true);
    }

    @Transactional
    public SocialLoginResult loginExistingAfterConflict(VerifiedSocialIdentity identity) {
        Member member = memberRepository
                .findBySocialAccount(identity.provider(), identity.providerUserId())
                .orElseThrow(SocialAccountConflictException::new);
        return loginExisting(member, identity);
    }

    private SocialLoginResult loginExisting(Member member, VerifiedSocialIdentity identity) {
        if (member.isDeleted()) {
            throw new IdentityException(IdentityErrorCode.WITHDRAWN_MEMBER);
        }
        member.updateProviderEmail(identity.providerUserId(), identity.email());
        Member savedMember = memberRepository.save(member);
        return issueSession(savedMember, false);
    }

    private SocialLoginResult issueSession(Member member, boolean isNewMember) {
        Instant issuedAt = clock.instant();
        IssuedAccessToken accessToken = accessTokenIssuer.issue(member.id(), issuedAt);
        String rawRefreshToken = refreshTokenGenerator.generate();
        String refreshTokenHash = refreshTokenHasher.hash(rawRefreshToken);
        Instant refreshTokenExpiresAt = issuedAt.plus(tokenPolicy.refreshTokenTtl());

        refreshSessionRepository.save(
                member.id(),
                refreshTokenHash,
                refreshTokenExpiresAt,
                issuedAt
        );

        return new SocialLoginResult(
                accessToken.value(),
                rawRefreshToken,
                SocialLoginResult.BEARER,
                accessToken.expiresInSeconds(),
                ChronoUnit.SECONDS.between(issuedAt, refreshTokenExpiresAt),
                isNewMember,
                new MemberResult(
                        member.id(),
                        member.name(),
                        member.profileImageUrl(),
                        member.lastSelectedBabyId()
                )
        );
    }
}
