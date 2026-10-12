package com.dearusback.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearusback.identity.application.model.SocialLoginCommand;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.model.SocialLoginResult.MemberResult;
import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.application.port.out.SocialIdentityVerifier;
import com.dearusback.identity.domain.exception.SocialAccountConflictException;
import com.dearusback.identity.domain.type.SocialProvider;
import org.junit.jupiter.api.Test;

class SocialLoginServiceTest {

    @Test
    void retriesAsExistingLoginAfterConcurrentInsertConflict() {
        SocialIdentityVerifier verifier = mock(SocialIdentityVerifier.class);
        SocialLoginTransactionService transactionService = mock(SocialLoginTransactionService.class);
        SocialLoginService service = new SocialLoginService(verifier, transactionService);
        SocialLoginCommand command = new SocialLoginCommand(SocialProvider.GOOGLE, "provider-token");
        VerifiedSocialIdentity identity = new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                "google-1",
                null,
                null,
                null
        );
        SocialLoginResult expected = new SocialLoginResult(
                "access",
                "refresh",
                "Bearer",
                1800,
                2592000,
                false,
                new MemberResult(1L, "사용자", null, null)
        );

        when(verifier.verify(SocialProvider.GOOGLE, "provider-token")).thenReturn(identity);
        when(transactionService.loginOrRegister(identity)).thenThrow(new SocialAccountConflictException());
        when(transactionService.loginExistingAfterConflict(identity)).thenReturn(expected);

        SocialLoginResult result = service.login(command);

        assertThat(result).isEqualTo(expected);
        verify(transactionService).loginExistingAfterConflict(identity);
    }
}
