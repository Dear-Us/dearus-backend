package com.dearusback.care.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface SpringDataCareRecordRepository extends JpaRepository<CareRecordJpaEntity, Long> {
    Optional<CareRecordJpaEntity> findByIdAndBabyIdAndDeletedFalse(Long id, Long babyId);
}
