package com.nexusworld.api.auth;

import java.util.List;

public record CurrentUserResponse(
        String contractVersion,
        String userId,
        String username,
        List<String> roles) {}
