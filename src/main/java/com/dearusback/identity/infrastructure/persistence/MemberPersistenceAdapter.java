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
}
