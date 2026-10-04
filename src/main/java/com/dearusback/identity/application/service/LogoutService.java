package com.dearusback.identity.application.service;

import com.dearusback.identity.application.model.LogoutCommand;
import com.dearusback.identity.application.port.in.LogoutUseCase;
import com.dearusback.identity.application.port.out.RefreshSessionRepository;
import com.dearusback.identity.application.port.out.RefreshTokenHasher;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class LogoutService implements LogoutUseCase {

    private final RefreshSessionRepository sessions;
    private final RefreshTokenHasher hasher;
    private final Clock clock;

    public LogoutService(RefreshSessionRepository sessions, RefreshTokenHasher hasher, Clock clock) {
        this.sessions = sessions;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Override
    public void logout(LogoutCommand command) {
        sessions.revokeByTokenHash(hasher.hash(command.refreshToken()), clock.instant());
    }
}
