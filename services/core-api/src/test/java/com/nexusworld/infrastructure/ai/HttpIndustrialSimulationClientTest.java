package com.nexusworld.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpIndustrialSimulationClientTest {
  @Test
  void postsJsonBodyAndValidatesResultEnvelope() throws Exception {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    HttpIndustrialSimulationClient client = new HttpIndustrialSimulationClient(
        builder.baseUrl("http://ai-service").build());
    var request = new ObjectMapper().readTree("""
        {"contractVersion":"v1","seed":1,"turns":1,"companies":[],
         "supplyLinks":[],"shocks":[]}
        """);
    server.expect(requestTo("http://ai-service/api/v1/simulations/execute"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json(request.toString()))
        .andRespond(withSuccess("""
            {"resultHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
             "snapshots":[],"finalState":[],"invariants":{"capacityRespected":true}}
            """, MediaType.APPLICATION_JSON));

    var result = client.execute(request);

    assertThat(result.resultHash()).hasSize(64);
    assertThat(result.invariants().path("capacityRespected").asBoolean()).isTrue();
    server.verify();
  }
}
