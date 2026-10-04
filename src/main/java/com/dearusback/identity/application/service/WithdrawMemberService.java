package com.dearusback.identity.application.service;

import com.dearusback.identity.application.port.in.WithdrawMemberUseCase;
import com.dearusback.identity.application.port.out.ProfileImageStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WithdrawMemberService implements WithdrawMemberUseCase {

    private static final Logger log = LoggerFactory.getLogger(WithdrawMemberService.class);
    private final WithdrawMemberTransactionService transaction;
    private final ProfileImageStoragePort storage;

    public WithdrawMemberService(WithdrawMemberTransactionService transaction, ProfileImageStoragePort storage) {
        this.transaction = transaction;
        this.storage = storage;
    }

    @Override
    public void withdraw(Long memberId) {
        String previousImageUrl = transaction.withdraw(memberId);
        if (previousImageUrl != null) {
            try {
                if (storage.owns(memberId, previousImageUrl)) {
                    storage.deleteOwnedImage(memberId, previousImageUrl);
                }
            } catch (RuntimeException exception) {
                log.warn("Profile image cleanup failed after member withdrawal for member {}", memberId, exception);
            }
        }
    }
}
