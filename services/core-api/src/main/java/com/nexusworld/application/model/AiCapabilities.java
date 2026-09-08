package com.nexusworld.application.model;

import java.util.List;

public record AiCapabilities(
        String status,
        String service,
        String contractVersion,
        List<String> capabilities) {
}

