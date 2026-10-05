package com.dearusback.care.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCareRecordRepository extends JpaRepository<CareRecordJpaEntity, Long> {
}
