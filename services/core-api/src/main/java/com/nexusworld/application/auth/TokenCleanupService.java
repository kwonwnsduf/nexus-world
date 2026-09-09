package com.nexusworld.application.auth;

import com.nexusworld.domain.auth.AccessTokenBlacklistRepository;
import com.nexusworld.domain.auth.RefreshTokenRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenCleanupService {
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenBlacklistRepository blacklist;
    private final Clock clock;

    public TokenCleanupService(
            RefreshTokenRepository refreshTokens,
            AccessTokenBlacklistRepository blacklist,
            Clock clock) {
        this.refreshTokens = refreshTokens;
        this.blacklist = blacklist;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${nexus.auth.token-cleanup-interval:PT1H}")
    @Transactional
    public void deleteExpiredTokens() {
        var now = clock.instant();
        refreshTokens.deleteByExpiresAtBefore(now);
        blacklist.deleteByExpiresAtBefore(now);
    }
}
