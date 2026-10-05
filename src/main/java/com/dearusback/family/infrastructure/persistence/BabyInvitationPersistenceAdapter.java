package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.domain.model.BabyInvitation;
import com.dearusback.family.domain.repository.BabyInvitationRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class BabyInvitationPersistenceAdapter implements BabyInvitationRepository {

    private final SpringDataBabyInvitationRepository repository;

    public BabyInvitationPersistenceAdapter(SpringDataBabyInvitationRepository repository) {
        this.repository = repository;
    }

    @Override
    public BabyInvitation save(BabyInvitation invitation) {
        try {
            return BabyInvitationMapper.toDomain(repository.saveAndFlush(BabyInvitationMapper.toEntity(invitation)));
        } catch (DataAccessException exception) {
            throw new IllegalStateException("Invitation persistence failed");
        }
    }
}
