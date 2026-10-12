package com.dearusback.identity.application.service;

import com.dearusback.identity.application.model.SocialLoginCommand;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.application.port.in.SocialLoginUseCase;
import com.dearusback.identity.application.port.out.SocialIdentityVerifier;
import com.dearusback.identity.domain.exception.SocialAccountConflictException;
import org.springframework.stereotype.Service;

@Service
public class SocialLoginService implements SocialLoginUseCase {

    private final SocialIdentityVerifier socialIdentityVerifier;
    private final SocialLoginTransactionService transactionService;

    public SocialLoginService(
            SocialIdentityVerifier socialIdentityVerifier,
            SocialLoginTransactionService transactionService
    ) {
        this.socialIdentityVerifier = socialIdentityVerifier;
        this.transactionService = transactionService;
    }

    @Override
    public SocialLoginResult login(SocialLoginCommand command) {
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                command.provider(),
                command.providerToken()
        );

        try {
            return transactionService.loginOrRegister(identity);
        } catch (SocialAccountConflictException exception) {
            return transactionService.loginExistingAfterConflict(identity);
        }
    }
}
