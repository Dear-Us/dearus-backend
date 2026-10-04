package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.domain.model.Baby;
import com.dearusback.family.domain.repository.BabyRepository;
import org.springframework.stereotype.Repository;

@Repository
public class BabyPersistenceAdapter implements BabyRepository {

    private final SpringDataBabyRepository repository;

    public BabyPersistenceAdapter(SpringDataBabyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Baby save(Baby baby) {
        return BabyMapper.toDomain(repository.saveAndFlush(BabyMapper.toEntity(baby)));
    }
}
