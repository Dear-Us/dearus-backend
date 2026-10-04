package com.dearusback.family.application.service;

import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.port.in.DeleteBabyUseCase;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeleteBabyService implements DeleteBabyUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteBabyService.class);
    private final BabyRegistrationMemberAccess members;
    private final BabyQueryPort babies;
    private final DeleteBabyTransactionService transaction;
    private final BabyProfileImageStoragePort storage;

    public DeleteBabyService(BabyRegistrationMemberAccess members, BabyQueryPort babies,
                             DeleteBabyTransactionService transaction, BabyProfileImageStoragePort storage) {
        this.members = members;
        this.babies = babies;
        this.transaction = transaction;
        this.storage = storage;
    }

    @Override
    public void delete(Long memberId, Long babyId) {
        members.requireActiveMember(memberId);
        MyBabySummary accessible = babies.findAccessibleBaby(memberId, babyId)
                .orElseThrow(() -> new FamilyException(FamilyErrorCode.BABY_NOT_FOUND));
        if (accessible.myRole() != BabyMemberRole.OWNER) {
            throw new FamilyException(FamilyErrorCode.BABY_DELETE_FORBIDDEN);
        }
        String previousImageUrl = transaction.delete(memberId, babyId);
        if (previousImageUrl != null) {
            try {
                storage.deleteOwnedImage(previousImageUrl);
            } catch (RuntimeException exception) {
                log.warn("Baby profile image cleanup failed after deletion", exception);
            }
        }
    }
}
