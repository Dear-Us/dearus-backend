package com.dearusback.identity.application.service;

import com.dearusback.identity.application.model.MyProfileResult;
import com.dearusback.identity.application.port.in.GetMyProfileQuery;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMyProfileService implements GetMyProfileQuery {

    private final MemberRepository members;

    public GetMyProfileService(MemberRepository members) {
        this.members = members;
    }

    @Override
    @Transactional(readOnly = true)
    public MyProfileResult getMyProfile(Long memberId) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.INVALID_AUTHENTICATION));
        if (member.isDeleted()) {
            throw new IdentityException(IdentityErrorCode.WITHDRAWN_MEMBER);
        }
        return MyProfileResult.from(member);
    }
}
