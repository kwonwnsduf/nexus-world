package com.nexusworld.infrastructure.ai;

import com.nexusworld.application.model.AiCapabilities;
import com.nexusworld.application.port.AiServiceClient;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpAiServiceClient implements AiServiceClient {
    private final RestClient restClient;

    public HttpAiServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${nexus.services.ai.base-url}") String aiServiceBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(aiServiceBaseUrl).build();
    }

    @Override
    public AiCapabilities getCapabilities() {
        try {
            AiCapabilitiesResponse response = restClient.get()
                    .uri("/api/v1/platform/capabilities")
                    .retrieve()
                    .body(AiCapabilitiesResponse.class);
            if (response == null) {
                throw new IllegalStateException("AI service returned an empty response");
            }
            return new AiCapabilities(
                    response.status(),
                    response.service(),
                    response.contractVersion(),
                    List.copyOf(response.capabilities()));
        } catch (RestClientException | IllegalStateException exception) {
            throw new AiServiceUnavailableException(exception);
        }
    }

    private record AiCapabilitiesResponse(
            String status,
            String service,
            String contractVersion,
            List<String> capabilities) {
    }
}

