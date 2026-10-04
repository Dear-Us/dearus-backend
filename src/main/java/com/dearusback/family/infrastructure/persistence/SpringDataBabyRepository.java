package com.dearusback.family.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBabyRepository extends JpaRepository<BabyJpaEntity, Long> {
}
