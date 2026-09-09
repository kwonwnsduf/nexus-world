package com.nexusworld.application.auth;

public record IssuedTokenPair(
        String accessToken,
        long accessExpiresInSeconds,
        String refreshToken,
        long refreshExpiresInSeconds) {}
