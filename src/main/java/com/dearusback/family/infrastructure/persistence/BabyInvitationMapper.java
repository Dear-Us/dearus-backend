package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.domain.model.BabyInvitation;

final class BabyInvitationMapper {

    private BabyInvitationMapper() {
    }

    static BabyInvitationJpaEntity toEntity(BabyInvitation invitation) {
        return new BabyInvitationJpaEntity(invitation.id(), invitation.babyId(),
                invitation.createdByMemberId(), invitation.tokenHash(), invitation.role(),
                invitation.status(), invitation.expiresAt(), invitation.acceptedByMemberId(),
                invitation.acceptedAt(), invitation.canceledAt(), invitation.createdAt(), invitation.updatedAt());
    }

    static BabyInvitation toDomain(BabyInvitationJpaEntity entity) {
        return new BabyInvitation(entity.id(), entity.babyId(), entity.createdByMemberId(),
                entity.tokenHash(), entity.role(), entity.status(), entity.expiresAt(),
                entity.acceptedByMemberId(), entity.acceptedAt(), entity.canceledAt(),
                entity.createdAt(), entity.updatedAt());
    }
}
