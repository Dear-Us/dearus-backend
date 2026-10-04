package com.dearusback.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.model.SocialAccount;
import com.dearusback.identity.domain.repository.MemberRepository;
import com.dearusback.identity.domain.type.SocialProvider;
import com.dearusback.support.domain.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetMyProfileServiceTest {

    @Test
    void returnsProfileWithoutProviderUserId() {
        Member member = new Member(7L, "기존 이름", "member@example.com", "Europe/Paris", "https://example.com/image.jpg",
                42L, false, List.of(
                new SocialAccount(1L, SocialProvider.GOOGLE, "private-google-id", "google@example.com"),
                new SocialAccount(2L, SocialProvider.KAKAO, "private-kakao-id", null)
        ));
        var result = new GetMyProfileService(repository(Optional.of(member))).getMyProfile(7L);

        assertThat(result.memberId()).isEqualTo(7L);
        assertThat(result.email()).isEqualTo("member@example.com");
        assertThat(result.memberName()).isEqualTo("기존 이름");
        assertThat(result.profileImageUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(result.timezone()).isEqualTo("Europe/Paris");
        assertThat(result.lastSelectedBabyId()).isEqualTo(42L);
        assertThat(result.socialAccounts()).hasSize(2);
        assertThat(result.socialAccounts().get(0).provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(result.socialAccounts().get(0).providerEmail()).isEqualTo("google@example.com");
        assertThat(result.socialAccounts().get(1).providerEmail()).isNull();
    }

    @Test
    void missingMemberIsUnauthorized() {
        assertThatThrownBy(() -> new GetMyProfileService(repository(Optional.empty())).getMyProfile(7L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(IdentityErrorCode.INVALID_AUTHENTICATION));
    }

    @Test
    void withdrawnMemberIsForbidden() {
        Member withdrawn = new Member(7L, "기존 이름", null, "Asia/Seoul", null, null, true, List.of());
        assertThatThrownBy(() -> new GetMyProfileService(repository(Optional.of(withdrawn))).getMyProfile(7L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(IdentityErrorCode.WITHDRAWN_MEMBER));
    }

    private MemberRepository repository(Optional<Member> member) {
        return new MemberRepository() {
            @Override
            public Optional<Member> findById(Long memberId) {
                return member;
            }

            @Override
            public Optional<Member> findByIdForUpdate(Long memberId) {
                return member;
            }

            @Override
            public Optional<Member> findBySocialAccount(SocialProvider provider, String providerUserId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Member save(Member member) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Member updateProfile(Member member) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int initializeLastSelectedBabyId(Long memberId, Long babyId) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
