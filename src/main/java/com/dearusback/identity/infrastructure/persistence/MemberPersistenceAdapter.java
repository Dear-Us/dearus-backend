package com.dearusback.identity.infrastructure.persistence;

import com.dearusback.identity.domain.exception.SocialAccountConflictException;
import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.repository.MemberRepository;
import com.dearusback.identity.domain.type.SocialProvider;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class MemberPersistenceAdapter implements MemberRepository {

    private final SpringDataMemberRepository repository;

    public MemberPersistenceAdapter(SpringDataMemberRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Member> findById(Long memberId) {
        return repository.findWithSocialAccountsById(memberId).map(MemberMapper::toDomain);
    }

    @Override
    public Optional<Member> findByIdForUpdate(Long memberId) {
        return repository.findByIdForUpdate(memberId).map(MemberMapper::toDomain);
    }

    @Override
    public Optional<Member> findBySocialAccount(SocialProvider provider, String providerUserId) {
        return repository.findBySocialAccount(provider, providerUserId).map(MemberMapper::toDomain);
    }

    @Override
    public Member save(Member member) {
        try {
            MemberJpaEntity saved = repository.saveAndFlush(MemberMapper.toJpaEntity(member));
            return MemberMapper.toDomain(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new SocialAccountConflictException();
        }
    }

    @Override
    public Member updateProfile(Member member) {
        MemberJpaEntity entity = repository.findById(member.id())
                .orElseThrow(() -> new IllegalStateException("Member disappeared during profile update"));
        entity.updateProfile(member.name(), member.profileImageUrl());
        repository.flush();
        return MemberMapper.toDomain(entity);
    }

    @Override
    public Member updateLastSelectedBabyId(Member member) {
        MemberJpaEntity entity = repository.findById(member.id())
                .orElseThrow(() -> new IllegalStateException("Member disappeared during baby selection"));
        entity.updateLastSelectedBabyId(member.lastSelectedBabyId());
        repository.flush();
        return MemberMapper.toDomain(entity);
    }

    @Override
    public void withdraw(Member member) {
        MemberJpaEntity entity = repository.findById(member.id())
                .orElseThrow(() -> new IllegalStateException("Member disappeared during withdrawal"));
        entity.withdraw(member.deletedAt());
        repository.flush();
    }

    @Override
    public int initializeLastSelectedBabyId(Long memberId, Long babyId) {
        return repository.initializeLastSelectedBabyId(memberId, babyId);
    }

    @Override
    public int clearLastSelectedBabyIfMatches(Long babyId) {
        return repository.clearLastSelectedBabyIfMatches(babyId);
    }

    @Override
    public int clearLastSelectedBabyIfMatches(Long memberId, Long babyId) {
        return repository.clearLastSelectedBabyIfMatches(memberId, babyId);
    }
}
