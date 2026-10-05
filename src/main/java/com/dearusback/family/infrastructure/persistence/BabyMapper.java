package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.domain.model.Baby;
import com.dearusback.family.domain.model.BabyMember;
import java.util.List;

final class BabyMapper {

    private BabyMapper() {
    }

    static BabyJpaEntity toEntity(Baby baby) {
        BabyJpaEntity entity = new BabyJpaEntity(baby.id(), baby.createdByMemberId(), baby.name(),
                baby.birthDate(), baby.gender(), baby.profileImageUrl(), baby.deleted(),
                baby.createdAt(), baby.updatedAt(), baby.deletedAt());
        baby.members().stream()
                .map(member -> new BabyMemberJpaEntity(member.id(), member.memberId(), member.role(),
                        member.status(), member.joinedAt(), member.endedAt()))
                .forEach(entity::addMember);
        return entity;
    }

    static Baby toDomain(BabyJpaEntity entity) {
        return toDomain(entity, entity.members());
    }

    static Baby toDomain(BabyJpaEntity entity, List<BabyMemberJpaEntity> members) {
        return new Baby(entity.id(), entity.createdByMemberId(), entity.name(), entity.birthDate(),
                entity.gender(), entity.profileImageUrl(), entity.deleted(), entity.createdAt(),
                entity.updatedAt(), entity.deletedAt(), members.stream()
                .map(member -> new BabyMember(member.id(), member.memberId(), member.role(), member.status(),
                member.joinedAt(), member.endedAt())).toList());
    }
}
