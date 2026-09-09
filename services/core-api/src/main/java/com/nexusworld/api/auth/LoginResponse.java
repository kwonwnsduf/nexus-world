package com.nexusworld.api.auth;

public record LoginResponse(
        String contractVersion,
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn) {}
