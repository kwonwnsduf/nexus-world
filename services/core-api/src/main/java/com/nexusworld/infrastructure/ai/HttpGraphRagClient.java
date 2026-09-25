package com.nexusworld.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.application.port.GraphRagClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpGraphRagClient implements GraphRagClient {
  private final RestClient client;

  public HttpGraphRagClient(RestClient.Builder builder,
      @Value("${nexus.services.ai.base-url}") String baseUrl) {
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(Duration.ofSeconds(5));
    requests.setReadTimeout(Duration.ofSeconds(20));
    client = builder.requestFactory(requests).baseUrl(baseUrl).build();
  }

  @Override
  public JsonNode query(UUID worldVersionId, String query, String authorization) {
    JsonNode value = client.post().uri("/api/v1/graphrag/query")
        .header(HttpHeaders.AUTHORIZATION, authorization)
        .body(Map.of("worldVersionId", worldVersionId, "query", query,
            "maxDepth", 3, "rootLimit", 5, "pathLimit", 10, "evidenceLimit", 5))
        .retrieve().body(JsonNode.class);
    if (value == null || !worldVersionId.toString().equals(value.path("worldVersionId").asText())) {
      throw new IllegalStateException("GraphRAG did not return the requested world version");
    }
    return value;
  }
}
