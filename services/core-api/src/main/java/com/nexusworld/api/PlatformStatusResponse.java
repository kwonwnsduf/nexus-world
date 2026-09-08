package com.nexusworld.api;

import com.nexusworld.application.model.AiCapabilities;

public record PlatformStatusResponse(
        String status,
        String service,
        String contractVersion,
        AiCapabilities downstream) {
}

