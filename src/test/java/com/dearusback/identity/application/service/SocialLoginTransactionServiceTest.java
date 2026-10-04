package com.dearusback.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearusback.identity.application.model.AuthTokenPolicy;
import com.dearusback.identity.application.model.IssuedAccessToken;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.application.port.out.RefreshSessionRepository;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.model.SocialAccount;
import com.dearusback.identity.domain.repository.MemberRepository;
import com.dearusback.identity.domain.type.SocialProvider;
import com.dearusback.identity.infrastructure.token.Sha256RefreshTokenHasher;
import com.dearusback.support.domain.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SocialLoginTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T00:00:00Z");

    private InMemoryMemberRepository memberRepository;
    private CapturingRefreshSessionRepository refreshSessionRepository;
    private SocialLoginTransactionService service;

    @BeforeEach
    void setUp() {
        memberRepository = new InMemoryMemberRepository();
        refreshSessionRepository = new CapturingRefreshSessionRepository();
        service = new SocialLoginTransactionService(
                memberRepository,
                refreshSessionRepository,
                (memberId, issuedAt) -> new IssuedAccessToken("access-" + memberId, 1800),
                () -> "raw-refresh-token",
                new Sha256RefreshTokenHasher(),
                new AuthTokenPolicy(Duration.ofDays(30)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void registersNewGoogleMemberAndSocialAccount() {
        SocialLoginResult result = service.loginOrRegister(identity(
                SocialProvider.GOOGLE,
                "google-1",
                "same@example.com",
                "김소은",
                "https://image.example/google.png"
        ));

        assertThat(result.newMember()).isTrue();
        assertThat(result.member().memberName()).isEqualTo("김소은");
        assertThat(result.member().profileImageUrl()).isEqualTo("https://image.example/google.png");
        assertThat(memberRepository.count()).isOne();
        assertThat(memberRepository.findBySocialAccount(SocialProvider.GOOGLE, "google-1"))
                .get()
                .extracting(Member::timezone)
                .isEqualTo("Asia/Seoul");
    }

    @Test
    void existingLoginDoesNotCreateMemberOrOverwriteDearUsProfile() {
        memberRepository.seed(new Member(
                10L,
                "DearUs 이름",
                "old@example.com",
                "Europe/Paris",
                "https://image.example/dearus.png",
                null,
                false,
                List.of(new SocialAccount(20L, SocialProvider.GOOGLE, "google-1", "old@example.com"))
        ));

        SocialLoginResult result = service.loginOrRegister(identity(
                SocialProvider.GOOGLE,
                "google-1",
                "new@example.com",
                "Provider 이름",
                "https://image.example/provider.png"
        ));

        assertThat(result.newMember()).isFalse();
        assertThat(result.member().memberName()).isEqualTo("DearUs 이름");
        assertThat(result.member().profileImageUrl()).isEqualTo("https://image.example/dearus.png");
        assertThat(memberRepository.count()).isOne();
        assertThat(memberRepository.findBySocialAccount(SocialProvider.GOOGLE, "google-1"))
                .get()
                .extracting(Member::timezone)
                .isEqualTo("Europe/Paris");
        assertThat(memberRepository.findBySocialAccount(SocialProvider.GOOGLE, "google-1"))
                .get()
                .extracting(member -> member.socialAccounts().getFirst().providerEmail())
                .isEqualTo("new@example.com");
    }

    @Test
    void sameEmailFromGoogleAndKakaoCreatesSeparateMembers() {
        service.loginOrRegister(identity(SocialProvider.GOOGLE, "google-1", "same@example.com", "Google", null));
        service.loginOrRegister(identity(SocialProvider.KAKAO, "kakao-1", "same@example.com", "Kakao", null));

        assertThat(memberRepository.count()).isEqualTo(2);
    }

    @Test
    void missingProfileUsesDefaultNameAndNullImage() {
        SocialLoginResult result = service.loginOrRegister(identity(
                SocialProvider.NAVER,
                "naver-1",
                null,
                null,
                null
        ));

        assertThat(result.member().memberName()).isEqualTo(Member.DEFAULT_NAME);
        assertThat(result.member().profileImageUrl()).isNull();
    }

    @Test
    void withdrawnMemberCannotLogin() {
        memberRepository.seed(new Member(
                10L,
                "탈퇴 회원",
                null,
                "Asia/Seoul",
                null,
                null,
                true,
                List.of(new SocialAccount(20L, SocialProvider.GOOGLE, "google-1", null))
        ));

        assertThatThrownBy(() -> service.loginOrRegister(identity(
                SocialProvider.GOOGLE,
                "google-1",
                null,
                null,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
                        .isEqualTo(IdentityErrorCode.WITHDRAWN_MEMBER));
    }

    @Test
    void storesOnlyHashOfRefreshTokenWithThirtyDayExpiry() {
        SocialLoginResult result = service.loginOrRegister(identity(
                SocialProvider.GOOGLE,
                "google-1",
                null,
                "사용자",
                null
        ));

        CapturedRefreshSession session = refreshSessionRepository.sessions.getFirst();
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(session.tokenHash()).hasSize(64).isNotEqualTo(result.refreshToken());
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(result.refreshTokenExpiresInSeconds()).isEqualTo(Duration.ofDays(30).toSeconds());
    }

    private VerifiedSocialIdentity identity(
            SocialProvider provider,
            String providerUserId,
            String email,
            String name,
            String image
    ) {
        return new VerifiedSocialIdentity(provider, providerUserId, email, name, image);
    }

    private static final class InMemoryMemberRepository implements MemberRepository {

        private final AtomicLong sequence = new AtomicLong();
        private final Map<String, Member> members = new LinkedHashMap<>();

        @Override
        public Optional<Member> findById(Long memberId) {
            return members.values().stream().filter(member -> member.id().equals(memberId)).findFirst();
        }

        @Override
        public Optional<Member> findByIdForUpdate(Long memberId) {
            return findById(memberId);
        }

        @Override
        public Optional<Member> findBySocialAccount(SocialProvider provider, String providerUserId) {
            return Optional.ofNullable(members.get(key(provider, providerUserId)));
        }

        @Override
        public Member save(Member member) {
            Member saved = member.id() == null ? withId(member, sequence.incrementAndGet()) : member;
            SocialAccount account = saved.socialAccounts().getFirst();
            members.put(key(account.provider(), account.providerUserId()), saved);
            return saved;
        }

        @Override
        public Member updateProfile(Member member) {
            return save(member);
        }

        @Override
        public int initializeLastSelectedBabyId(Long memberId, Long babyId) {
            throw new UnsupportedOperationException();
        }

        void seed(Member member) {
            SocialAccount account = member.socialAccounts().getFirst();
            members.put(key(account.provider(), account.providerUserId()), member);
            sequence.updateAndGet(current -> Math.max(current, member.id()));
        }

        int count() {
            return members.size();
        }

        private Member withId(Member member, long id) {
            return new Member(
                    id,
                    member.name(),
                    member.email(),
                    member.timezone(),
                    member.profileImageUrl(),
                    member.lastSelectedBabyId(),
                    member.isDeleted(),
                    member.socialAccounts()
            );
        }

        private String key(SocialProvider provider, String providerUserId) {
            return provider + ":" + providerUserId;
        }
    }

    private static final class CapturingRefreshSessionRepository implements RefreshSessionRepository {

        private final List<CapturedRefreshSession> sessions = new ArrayList<>();

        @Override
        public Optional<com.dearusback.identity.domain.model.RefreshSession> findByTokenHash(String tokenHash) {
            return Optional.empty();
        }

        @Override
        public boolean revokeIfActive(Long sessionId, Instant now) {
            return false;
        }

        @Override
        public boolean revokeByTokenHash(String tokenHash, Instant revokedAt) {
            return false;
        }

        @Override
        public void save(Long memberId, String tokenHash, Instant expiresAt, Instant createdAt) {
            sessions.add(new CapturedRefreshSession(memberId, tokenHash, expiresAt, createdAt));
        }
    }

    private record CapturedRefreshSession(
            Long memberId,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt
    ) {
    }
}
