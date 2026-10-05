package com.dearusback.family.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBabyInvitationRepository extends JpaRepository<BabyInvitationJpaEntity, Long> {
}
