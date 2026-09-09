package com.nexusworld.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.auth")
public record AuthProperties(
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String secretBase64,
        Bootstrap bootstrap) {
    public record Bootstrap(boolean enabled, String username, String password) {}
}
