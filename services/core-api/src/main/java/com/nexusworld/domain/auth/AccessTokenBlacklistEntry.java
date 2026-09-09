package com.nexusworld.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "access_token_blacklist")
public class AccessTokenBlacklistEntry {
    @Id
    @Column(name = "jti")
    private UUID jti;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    @Column(nullable = false, length = 32)
    private String reason;

    protected AccessTokenBlacklistEntry() {}

    public AccessTokenBlacklistEntry(
            UUID jti, UUID userId, Instant expiresAt, Instant revokedAt, String reason) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.reason = reason;
    }
}
