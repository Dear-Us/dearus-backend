package com.dearusback.family.application.service;

import com.dearusback.family.application.model.TransferBabyOwnershipResult;
import com.dearusback.family.application.port.in.TransferBabyOwnershipUseCase;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyMember;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.family.domain.model.BabyMemberStatus;
import com.dearusback.family.domain.repository.BabyRepository;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferBabyOwnershipService implements TransferBabyOwnershipUseCase {

    private final BabyRegistrationMemberAccess members;
    private final BabyRepository babies;

    public TransferBabyOwnershipService(BabyRegistrationMemberAccess members, BabyRepository babies) {
        this.members = members;
        this.babies = babies;
    }

    @Override
    @Transactional
    public TransferBabyOwnershipResult transfer(Long requesterId, Long babyId, Long targetMemberId) {
        members.requireActiveMember(requesterId);
        babies.findByIdForUpdate(babyId, requesterId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        BabyMember requester = babies.findMemberForUpdate(babyId, requesterId)
                .filter(member -> member.status() == BabyMemberStatus.ACTIVE)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        if (requester.role() != BabyMemberRole.OWNER) {
            throw new FamilyException(FamilyErrorCode.BABY_MEMBER_MANAGEMENT_FORBIDDEN);
        }
        BabyMember target = babies.findMemberForUpdate(babyId, targetMemberId)
                .filter(member -> member.status() == BabyMemberStatus.ACTIVE)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_MEMBER_NOT_FOUND));
        if (target.role() == BabyMemberRole.OWNER) {
            throw new FamilyException(FamilyErrorCode.BABY_OWNER_TRANSFER_CONFLICT);
        }
        if (!members.isActiveMember(targetMemberId)) {
            throw new IllegalStateException("Active caregiver references an inactive member");
        }
        babies.transferOwnership(babyId, requester.demoteToCaregiver(), target.promoteToOwner());
        return new TransferBabyOwnershipResult(babyId, requesterId, targetMemberId);
    }
}
