package com.nexusworld.application.auth;

import com.nexusworld.domain.auth.AccessTokenBlacklistEntry;
import com.nexusworld.domain.auth.AccessTokenBlacklistRepository;
import com.nexusworld.domain.auth.RefreshToken;
import com.nexusworld.domain.auth.RefreshTokenRepository;
import com.nexusworld.domain.auth.UserAccount;
import com.nexusworld.domain.auth.UserRepository;
import com.nexusworld.security.CustomUserDetails;
import com.nexusworld.security.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenBlacklistRepository blacklist;
    private final JwtTokenProvider tokens;
    private final Clock clock;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            AccessTokenBlacklistRepository blacklist,
            JwtTokenProvider tokens,
            Clock clock) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.blacklist = blacklist;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public IssuedTokenPair login(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
            UserAccount user = users.findById(principal.getUserId()).orElseThrow(InvalidCredentialsException::new);
            user.recordSuccessfulLogin(clock.instant());
            return issueTokenPair(principal, user, UUID.randomUUID(), null);
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException();
        }
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedTokenPair refresh(String rawRefreshToken) {
        JwtTokenProvider.TokenClaims claims = parseRefreshToken(rawRefreshToken);
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();

        if (!current.getId().equals(claims.tokenId())
                || !current.getUser().getId().equals(claims.userId())) {
            throw new InvalidRefreshTokenException();
        }
        if (current.isUsedOrRevoked()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            current.revoke(now);
            throw new InvalidRefreshTokenException();
        }

        CustomUserDetails principal = CustomUserDetails.from(current.getUser());
        return issueTokenPair(principal, current.getUser(), current.getFamilyId(), current);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public void logout(String rawAccessToken, String rawRefreshToken) {
        JwtTokenProvider.TokenClaims access = parseAccessToken(rawAccessToken);
        JwtTokenProvider.TokenClaims refresh = parseRefreshToken(rawRefreshToken);
        RefreshToken storedRefresh = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!access.userId().equals(refresh.userId())
                || !storedRefresh.getId().equals(refresh.tokenId())
                || !storedRefresh.getUser().getId().equals(access.userId())) {
            throw new InvalidRefreshTokenException();
        }

        Instant now = clock.instant();
        refreshTokens.revokeFamily(storedRefresh.getFamilyId(), now);
        if (!blacklist.existsById(access.tokenId())) {
            blacklist.save(new AccessTokenBlacklistEntry(
                    access.tokenId(), access.userId(), access.expiresAt(), now, "LOGOUT"));
        }
    }

    private IssuedTokenPair issueTokenPair(
            CustomUserDetails principal,
            UserAccount user,
            UUID familyId,
            RefreshToken previous) {
        JwtTokenProvider.CreatedToken access = tokens.createAccessToken(principal);
        JwtTokenProvider.CreatedToken refresh = tokens.createRefreshToken(principal);
        if (previous != null) {
            previous.markUsed(clock.instant(), refresh.id());
        }
        refreshTokens.save(new RefreshToken(
                refresh.id(),
                familyId,
                user,
                hash(refresh.value()),
                refresh.issuedAt(),
                refresh.expiresAt()));
        return new IssuedTokenPair(
                access.value(),
                tokens.getAccessExpiresInSeconds(),
                refresh.value(),
                tokens.getRefreshExpiresInSeconds());
    }

    private JwtTokenProvider.TokenClaims parseAccessToken(String token) {
        try {
            return tokens.parseAccessToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidRefreshTokenException();
        }
    }

    private JwtTokenProvider.TokenClaims parseRefreshToken(String token) {
        try {
            return tokens.parseRefreshToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidRefreshTokenException();
        }
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
