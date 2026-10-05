package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.domain.model.BabyInvitation;
import com.dearusback.family.domain.repository.BabyInvitationRepository;
import java.util.List;
import java.util.Optional;
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

    @Override
    public List<BabyInvitation> findAllByBabyIdForUpdate(Long babyId) {
        return repository.findAllByBabyIdForUpdate(babyId).stream()
                .map(BabyInvitationMapper::toDomain).toList();
    }

    @Override
    public Optional<BabyInvitation> findByIdAndBabyIdForUpdate(Long invitationId, Long babyId) {
        return repository.findByIdAndBabyIdForUpdate(invitationId, babyId)
                .map(BabyInvitationMapper::toDomain);
    }

    @Override
    public Optional<BabyInvitation> findByIdForUpdate(Long invitationId) {
        return repository.findByIdForUpdate(invitationId).map(BabyInvitationMapper::toDomain);
    }

    @Override
    public Optional<BabyInvitation> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(BabyInvitationMapper::toDomain);
    }
}
