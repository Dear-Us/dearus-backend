package com.dearusback.identity.infrastructure.persistence;

import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.model.SocialAccount;

final class MemberMapper {

    private MemberMapper() {
    }

    static MemberJpaEntity toJpaEntity(Member member) {
        MemberJpaEntity entity = new MemberJpaEntity(
                member.id(),
                member.name(),
                member.email(),
                member.profileImageUrl(),
                member.lastSelectedBabyId(),
                member.isDeleted()
        );
        member.socialAccounts().stream()
                .map(MemberMapper::toJpaEntity)
                .forEach(entity::addSocialAccount);
        return entity;
    }

    static Member toDomain(MemberJpaEntity entity) {
        return new Member(
                entity.id(),
                entity.name(),
                entity.email(),
                entity.profileImageUrl(),
                entity.lastSelectedBabyId(),
                entity.deleted(),
                entity.socialAccounts().stream().map(MemberMapper::toDomain).toList()
        );
    }

    private static SocialAccountJpaEntity toJpaEntity(SocialAccount account) {
        return new SocialAccountJpaEntity(
                account.id(),
                account.provider(),
                account.providerUserId(),
                account.providerEmail()
        );
    }

    private static SocialAccount toDomain(SocialAccountJpaEntity entity) {
        return new SocialAccount(
                entity.id(),
                entity.provider(),
                entity.providerUserId(),
                entity.providerEmail()
        );
    }
}
