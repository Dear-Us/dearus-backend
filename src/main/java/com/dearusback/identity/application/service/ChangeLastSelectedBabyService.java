package com.dearusback.identity.application.service;

import com.dearusback.identity.application.model.ChangeLastSelectedBabyCommand;
import com.dearusback.identity.application.port.in.ChangeLastSelectedBabyUseCase;
import com.dearusback.identity.application.port.out.AccessibleBabyPort;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeLastSelectedBabyService implements ChangeLastSelectedBabyUseCase {

    private final MemberRepository members;
    private final AccessibleBabyPort accessibleBabies;

    public ChangeLastSelectedBabyService(MemberRepository members, AccessibleBabyPort accessibleBabies) {
        this.members = members;
        this.accessibleBabies = accessibleBabies;
    }

    @Override
    @Transactional
    public Long change(ChangeLastSelectedBabyCommand command) {
        Member member = members.findByIdForUpdate(command.memberId())
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.INVALID_AUTHENTICATION));
        if (member.isDeleted()) {
            throw new IdentityException(IdentityErrorCode.WITHDRAWN_MEMBER);
        }
        if (!accessibleBabies.existsActiveMembership(member.id(), command.babyId())) {
            throw new IdentityException(IdentityErrorCode.ACCESSIBLE_BABY_NOT_FOUND);
        }
        return members.updateLastSelectedBabyId(member.changeLastSelectedBabyId(command.babyId()))
                .lastSelectedBabyId();
    }
}
