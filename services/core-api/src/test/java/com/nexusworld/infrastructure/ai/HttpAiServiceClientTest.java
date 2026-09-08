package com.nexusworld.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nexusworld.application.model.AiCapabilities;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpAiServiceClientTest {
    @Test
    void mapsAiCapabilitiesV1Contract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpAiServiceClient client = new HttpAiServiceClient(builder, "http://ai-service");

        server.expect(requestTo("http://ai-service/api/v1/platform/capabilities"))
                .andRespond(withSuccess("""
                        {
                          "status": "UP",
                          "service": "ai-service",
                          "contractVersion": "v1",
                          "capabilities": ["retrieval", "deterministic-simulation"]
                        }
                        """, MediaType.APPLICATION_JSON));

        AiCapabilities result = client.getCapabilities();

        assertThat(result.status()).isEqualTo("UP");
        assertThat(result.service()).isEqualTo("ai-service");
        assertThat(result.contractVersion()).isEqualTo("v1");
        assertThat(result.capabilities()).containsExactly("retrieval", "deterministic-simulation");
        server.verify();
    }
}

