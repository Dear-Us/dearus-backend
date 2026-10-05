package com.dearusback.family.infrastructure.persistence;

import com.dearusback.family.domain.model.Baby;
import com.dearusback.family.domain.model.BabyMember;
import com.dearusback.family.domain.repository.BabyRepository;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.family.domain.model.BabyMemberStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BabyPersistenceAdapter implements BabyRepository {

    private final SpringDataBabyRepository repository;
    private final SpringDataBabyMemberRepository memberships;
    private final JdbcTemplate jdbc;

    public BabyPersistenceAdapter(SpringDataBabyRepository repository, SpringDataBabyMemberRepository memberships,
                                  JdbcTemplate jdbc) {
        this.repository = repository;
        this.memberships = memberships;
        this.jdbc = jdbc;
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
            var lockedMembers = memberships.findAllByBabyIdForUpdate(babyId);
            return BabyMapper.toDomain(entity, lockedMembers);
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
        for (BabyMemberJpaEntity membership : memberships.findAllByBabyIdForUpdate(baby.id())) {
            BabyMember changed = changedMembers.get(membership.id());
            membership.updateStatus(changed.status(), changed.endedAt());
        }
        repository.flush();
    }

    @Override
    public void saveCaregiverMembership(Baby baby, Long memberId) {
        BabyJpaEntity entity = repository.findByIdForUpdate(baby.id())
                .orElseThrow(() -> new IllegalStateException("Baby disappeared during caregiver connection"));
        BabyMember target = baby.members().stream()
                .filter(member -> member.memberId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Caregiver membership missing from aggregate"));
        BabyMemberJpaEntity existing = memberships.findByBabyAndMemberForUpdate(baby.id(), memberId)
                .orElse(null);
        if (existing == null) {
            BabyMemberJpaEntity added = new BabyMemberJpaEntity(null, target.memberId(), target.role(),
                    target.status(), target.joinedAt(), target.endedAt());
            entity.addMember(added);
            memberships.saveAndFlush(added);
        } else {
            existing.reactivateAsCaregiver(target.joinedAt());
            memberships.flush();
        }
    }

    @Override
    public Optional<BabyMember> findMemberForUpdate(Long babyId, Long memberId) {
        // A current locking read must not reuse a stale BabyMember from JPA's first-level cache.
        return jdbc.query("SELECT baby_member_id, member_id, role, status, joined_at, ended_at "
                + "FROM baby_member WHERE baby_id = ? AND member_id = ? FOR UPDATE", result -> {
            if (!result.next()) {
                return Optional.empty();
            }
            LocalDateTime endedAt = result.getObject("ended_at", LocalDateTime.class);
            return Optional.of(new BabyMember(result.getLong("baby_member_id"), result.getLong("member_id"),
                    BabyMemberRole.valueOf(result.getString("role")),
                    BabyMemberStatus.valueOf(result.getString("status")),
                    result.getObject("joined_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    endedAt == null ? null : endedAt.toInstant(ZoneOffset.UTC)));
        }, babyId, memberId);
    }

    @Override
    public int updateMembershipStatus(Long babyId, BabyMember changed) {
        return jdbc.update("UPDATE baby_member SET status = ?, ended_at = ? WHERE baby_id = ? "
                + "AND baby_member_id = ? AND member_id = ? AND role = 'CAREGIVER' AND status = 'ACTIVE'",
                changed.status().name(), LocalDateTime.ofInstant(changed.endedAt(), ZoneOffset.UTC),
                babyId, changed.id(), changed.memberId());
    }

    @Override
    public void transferOwnership(Long babyId, BabyMember previousOwner, BabyMember newOwner) {
        int demoted = jdbc.update("UPDATE baby_member SET role = 'CAREGIVER' WHERE baby_id = ? "
                + "AND baby_member_id = ? AND member_id = ? AND role = 'OWNER' AND status = 'ACTIVE'",
                babyId, previousOwner.id(), previousOwner.memberId());
        if (demoted != 1) {
            throw new IllegalStateException("Previous owner changed during transfer");
        }
        int promoted = jdbc.update("UPDATE baby_member SET role = 'OWNER' WHERE baby_id = ? "
                + "AND baby_member_id = ? AND member_id = ? AND role = 'CAREGIVER' AND status = 'ACTIVE'",
                babyId, newOwner.id(), newOwner.memberId());
        if (promoted != 1) {
            throw new IllegalStateException("Target caregiver changed during transfer");
        }
        Integer owners = jdbc.queryForObject("SELECT COUNT(*) FROM baby_member WHERE baby_id = ? "
                + "AND role = 'OWNER' AND status = 'ACTIVE'", Integer.class, babyId);
        if (owners == null || owners != 1) {
            throw new IllegalStateException("Ownership transfer must leave exactly one active owner");
        }
    }
}
