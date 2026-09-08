package com.nexusworld.api;

import com.nexusworld.application.PlatformStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformStatusController {
    private final PlatformStatusService platformStatusService;

    public PlatformStatusController(PlatformStatusService platformStatusService) {
        this.platformStatusService = platformStatusService;
    }

    @GetMapping("/status")
    public PlatformStatusResponse status() {
        return new PlatformStatusResponse(
                "UP",
                "core-api",
                "v1",
                platformStatusService.getAiCapabilities());
    }
}

