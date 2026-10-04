package com.dearusback.identity.application.service;

import com.dearusback.identity.application.model.BabyRegistrationMember;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.repository.MemberRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BabyRegistrationMemberService implements BabyRegistrationMemberAccess {

    private final MemberRepository members;

    public BabyRegistrationMemberService(MemberRepository members) {
        this.members = members;
    }

    @Override
    public BabyRegistrationMember requireActiveMember(Long memberId) {
        return requireActive(members.findById(memberId));
    }

    @Override
    public BabyRegistrationMember lockActiveMember(Long memberId) {
        return requireActive(members.findByIdForUpdate(memberId));
    }

    @Override
    public void initializeLastSelectedBabyIdIfAbsent(Long memberId, Long babyId) {
        members.initializeLastSelectedBabyId(memberId, babyId);
    }

    private BabyRegistrationMember requireActive(Optional<Member> found) {
        Member member = found.orElseThrow(() -> new IdentityException(IdentityErrorCode.INVALID_AUTHENTICATION));
        if (member.isDeleted()) {
            throw new IdentityException(IdentityErrorCode.WITHDRAWN_MEMBER);
        }
        return new BabyRegistrationMember(member.id(), member.timezone());
    }
}
