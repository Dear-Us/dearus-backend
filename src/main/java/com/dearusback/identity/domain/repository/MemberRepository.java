package com.dearusback.identity.domain.repository;

import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.type.SocialProvider;
import java.util.Optional;

public interface MemberRepository {

    Optional<Member> findById(Long memberId);

    Optional<Member> findByIdForUpdate(Long memberId);

    Optional<Member> findBySocialAccount(SocialProvider provider, String providerUserId);

    Member save(Member member);

    Member updateProfile(Member member);

    Member updateLastSelectedBabyId(Member member);

    void withdraw(Member member);

    int initializeLastSelectedBabyId(Long memberId, Long babyId);

    int clearLastSelectedBabyIfMatches(Long babyId);
}
