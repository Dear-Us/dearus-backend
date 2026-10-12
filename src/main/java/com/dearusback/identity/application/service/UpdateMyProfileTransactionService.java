package com.dearusback.identity.application.service;

import com.dearusback.identity.application.model.MyProfileResult;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.domain.model.Member;
import com.dearusback.identity.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateMyProfileTransactionService {

    private final MemberRepository members;

    public UpdateMyProfileTransactionService(MemberRepository members) {
        this.members = members;
    }

    @Transactional
    public UpdatedProfile update(Long memberId, String memberName, boolean removeProfileImage, String newImageUrl) {
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new IdentityException(IdentityErrorCode.INVALID_AUTHENTICATION));
        if (member.isDeleted()) {
            throw new IdentityException(IdentityErrorCode.WITHDRAWN_MEMBER);
        }
        String previousImageUrl = member.profileImageUrl();
        if (memberName != null) {
            member = member.changeName(memberName);
        }
        if (removeProfileImage) {
            member = member.removeProfileImage();
        } else if (newImageUrl != null) {
            member = member.changeProfileImage(newImageUrl);
        }
        return new UpdatedProfile(MyProfileResult.from(members.updateProfile(member)), previousImageUrl);
    }

    public record UpdatedProfile(MyProfileResult profile, String previousImageUrl) {
    }
}
