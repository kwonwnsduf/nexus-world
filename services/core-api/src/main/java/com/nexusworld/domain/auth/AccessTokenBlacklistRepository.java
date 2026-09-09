package com.nexusworld.domain.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessTokenBlacklistRepository extends JpaRepository<AccessTokenBlacklistEntry, UUID> {
    long deleteByExpiresAtBefore(Instant cutoff);
}
