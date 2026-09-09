package com.nexusworld.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username is required") @Size(max = 80) String username,
        @NotBlank(message = "password is required") @Size(max = 128) String password) {}
