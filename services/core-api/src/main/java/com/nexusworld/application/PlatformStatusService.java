package com.nexusworld.application;

import com.nexusworld.application.model.AiCapabilities;
import com.nexusworld.application.port.AiServiceClient;
import org.springframework.stereotype.Service;

@Service
public class PlatformStatusService {
    private final AiServiceClient aiServiceClient;

    public PlatformStatusService(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    public AiCapabilities getAiCapabilities() {
        return aiServiceClient.getCapabilities();
    }
}

