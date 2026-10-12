package com.dearusback.growth.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataGrowthRecordRepository extends JpaRepository<GrowthRecordJpaEntity, Long> {
    List<GrowthRecordJpaEntity> findByBabyIdAndDeletedFalseOrderByMeasuredDateDescIdDesc(Long babyId);

    Optional<GrowthRecordJpaEntity> findByIdAndBabyIdAndDeletedFalse(Long id, Long babyId);
}
