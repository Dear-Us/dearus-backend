package com.dearusback.identity.application.service;

import com.dearusback.identity.application.port.out.MemberWithdrawalFamilyPort;
import com.dearusback.identity.application.port.out.RefreshSessionRepository;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.repository.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawMemberTransactionService {

    private final MemberRepository members;
    private final MemberWithdrawalFamilyPort families;
    private final RefreshSessionRepository sessions;
    private final Clock clock;

    public WithdrawMemberTransactionService(MemberRepository members, MemberWithdrawalFamilyPort families,
                                            RefreshSessionRepository sessions, Clock clock) {
        this.members = members;
        this.families = families;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public String withdraw(Long memberId) {
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.INVALID_AUTHENTICATION));
        if (member.isDeleted()) {
            throw new IdentityException(IdentityErrorCode.WITHDRAWN_MEMBER);
        }
        Instant now = clock.instant();
        if (!families.leaveCaregiverMembershipsIfNoActiveOwner(memberId, now)) {
            throw new IdentityException(IdentityErrorCode.ACTIVE_OWNED_BABY_EXISTS);
        }
        sessions.revokeAllForMember(memberId, now);
        members.withdraw(member.withdraw(now));
        return member.profileImageUrl();
    }
}
