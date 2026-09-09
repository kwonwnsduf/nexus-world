package com.nexusworld.security;

import com.nexusworld.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final AuthProperties properties;
    private final Clock clock;
    private final SecretKey key;

    public JwtTokenProvider(AuthProperties properties, Clock clock) {
        validate(properties);
        this.properties = properties;
        this.clock = clock;
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(properties.secretBase64());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("nexus.auth.secret-base64 must be valid Base64", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("nexus.auth.secret-base64 must decode to at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public CreatedToken createAccessToken(CustomUserDetails principal) {
        List<String> roles = principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .sorted()
                .toList();
        return createToken(principal, ACCESS_TOKEN_TYPE, properties.accessTokenTtl(), roles);
    }

    public CreatedToken createRefreshToken(CustomUserDetails principal) {
        return createToken(principal, REFRESH_TOKEN_TYPE, properties.refreshTokenTtl(), List.of());
    }

    public TokenClaims parseAccessToken(String token) {
        return parseToken(token, ACCESS_TOKEN_TYPE);
    }

    public TokenClaims parseRefreshToken(String token) {
        return parseToken(token, REFRESH_TOKEN_TYPE);
    }

    public long getAccessExpiresInSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    public long getRefreshExpiresInSeconds() {
        return properties.refreshTokenTtl().toSeconds();
    }

    private CreatedToken createToken(
            CustomUserDetails principal,
            String tokenType,
            Duration ttl,
            List<String> roles) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        UUID tokenId = UUID.randomUUID();
        var builder = Jwts.builder()
                .issuer(properties.issuer())
                .subject(principal.getUserId().toString())
                .audience().add(properties.audience()).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(tokenId.toString())
                .claim("username", principal.getUsername())
                .claim(TOKEN_TYPE_CLAIM, tokenType);
        if (!roles.isEmpty()) {
            builder.claim("roles", roles);
        }
        String value = builder.signWith(key, Jwts.SIG.HS256).compact();
        return new CreatedToken(value, tokenId, issuedAt, expiresAt);
    }

    private TokenClaims parseToken(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!claims.getAudience().contains(properties.audience())) {
            throw new IllegalArgumentException("JWT audience is invalid");
        }
        if (!expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("JWT token type is invalid");
        }
        return new TokenClaims(
                UUID.fromString(claims.getSubject()),
                claims.get("username", String.class),
                UUID.fromString(claims.getId()),
                claims.getExpiration().toInstant());
    }

    private void validate(AuthProperties properties) {
        if (properties.issuer() == null || properties.issuer().isBlank()
                || properties.audience() == null || properties.audience().isBlank()) {
            throw new IllegalStateException("JWT issuer and audience must not be blank");
        }
        if (!isPositive(properties.accessTokenTtl()) || !isPositive(properties.refreshTokenTtl())) {
            throw new IllegalStateException("JWT access and refresh token TTLs must be positive");
        }
        if (properties.refreshTokenTtl().compareTo(properties.accessTokenTtl()) <= 0) {
            throw new IllegalStateException("JWT refresh token TTL must be longer than access token TTL");
        }
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    public record CreatedToken(String value, UUID id, Instant issuedAt, Instant expiresAt) {}

    public record TokenClaims(UUID userId, String username, UUID tokenId, Instant expiresAt) {}
}
