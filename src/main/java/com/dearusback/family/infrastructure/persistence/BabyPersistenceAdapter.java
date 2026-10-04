package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.domain.model.Baby;
import com.dearusback.family.domain.model.BabyMember;
import com.dearusback.family.domain.repository.BabyRepository;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class BabyPersistenceAdapter implements BabyRepository {

    private final SpringDataBabyRepository repository;
    private final SpringDataBabyMemberRepository memberships;

    public BabyPersistenceAdapter(SpringDataBabyRepository repository, SpringDataBabyMemberRepository memberships) {
        this.repository = repository;
        this.memberships = memberships;
    }

    @Override
    public Baby save(Baby baby) {
        return BabyMapper.toDomain(repository.saveAndFlush(BabyMapper.toEntity(baby)));
    }

    @Override
    public Optional<Baby> findByIdForUpdate(Long babyId, Long memberId) {
        return repository.findByIdForUpdate(babyId).map(entity -> {
            memberships.findByBabyAndMemberForUpdate(babyId, memberId);
            return BabyMapper.toDomain(entity);
        });
    }

    @Override
    public Optional<Baby> findByIdAndAllMembersForUpdate(Long babyId) {
        return repository.findByIdForUpdate(babyId).map(entity -> {
            memberships.findAllByBabyIdForUpdate(babyId);
            return BabyMapper.toDomain(entity);
        });
    }

    @Override
    public Baby update(Baby baby) {
        BabyJpaEntity entity = repository.findById(baby.id())
                .orElseThrow(() -> new IllegalStateException("Baby disappeared during update"));
        entity.updateInformation(baby.name(), baby.birthDate(), baby.gender(), baby.profileImageUrl(),
                baby.updatedAt());
        repository.flush();
        return BabyMapper.toDomain(entity);
    }

    @Override
    public void markDeleted(Baby baby) {
        BabyJpaEntity entity = repository.findById(baby.id())
                .orElseThrow(() -> new IllegalStateException("Baby disappeared during deletion"));
        Map<Long, BabyMember> changedMembers = baby.members().stream()
                .collect(Collectors.toMap(BabyMember::id, Function.identity()));
        entity.markDeleted(baby.deletedAt());
        for (BabyMemberJpaEntity membership : entity.members()) {
            BabyMember changed = changedMembers.get(membership.id());
            membership.updateStatus(changed.status(), changed.endedAt());
        }
        repository.flush();
    }
}
