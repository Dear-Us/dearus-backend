package com.dearusback.growth.infrastructure.persistence;

import com.dearusback.growth.domain.model.GrowthRecord;
import com.dearusback.growth.domain.repository.GrowthRecordRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class GrowthRecordPersistenceAdapter implements GrowthRecordRepository {
    private final SpringDataGrowthRecordRepository records;
    private final EntityManager entityManager;

    public GrowthRecordPersistenceAdapter(SpringDataGrowthRecordRepository records, EntityManager entityManager) {
        this.records = records;
        this.entityManager = entityManager;
    }

    @Override
    public GrowthRecord save(GrowthRecord record) {
        GrowthRecordJpaEntity saved = records.saveAndFlush(new GrowthRecordJpaEntity(record));
        return record.persisted(saved.id(), saved.version());
    }

    @Override
    public GrowthRecord update(GrowthRecord record) {
        GrowthRecordJpaEntity entity = records.findByIdAndBabyIdAndDeletedFalse(record.id(), record.babyId())
                .orElseThrow(() -> new OptimisticLockingFailureException("Growth record was deleted"));
        if (entity.version() != record.version()) {
            throw new OptimisticLockingFailureException("Growth record version changed");
        }
        entity.apply(record);
        entityManager.flush();
        if (entity.version() == record.version()) {
            // A same-value PATCH is not dirty to JPA, so advance its version with the same CAS guard.
            int updated = entityManager.createQuery("UPDATE GrowthRecordJpaEntity record "
                            + "SET record.version = record.version + 1 "
                            + "WHERE record.id = :id AND record.babyId = :babyId "
                            + "AND record.version = :version AND record.deleted = false")
                    .setParameter("id", record.id())
                    .setParameter("babyId", record.babyId())
                    .setParameter("version", record.version())
                    .executeUpdate();
            if (updated != 1) {
                throw new OptimisticLockingFailureException("Growth record version changed");
            }
            entityManager.detach(entity);
            return record.persisted(entity.id(), record.version() + 1);
        }
        return record.persisted(entity.id(), entity.version());
    }

    @Override
    public List<GrowthRecord> findAllByBaby(Long babyId) {
        return records.findByBabyIdAndDeletedFalseOrderByMeasuredDateDescIdDesc(babyId)
                .stream().map(GrowthRecordJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<GrowthRecord> findByBabyAndId(Long babyId, Long growthRecordId) {
        return records.findByIdAndBabyIdAndDeletedFalse(growthRecordId, babyId)
                .map(GrowthRecordJpaEntity::toDomain);
    }
}
