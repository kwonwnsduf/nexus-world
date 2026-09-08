package com.nexusworld;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NexusWorldApplicationTest {
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReportsUp() {
        String response = restTemplate.getForObject(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response).contains("\"status\":\"UP\"");
        assertThat(response).contains("\"service\":\"core-api\"");
    }
}

