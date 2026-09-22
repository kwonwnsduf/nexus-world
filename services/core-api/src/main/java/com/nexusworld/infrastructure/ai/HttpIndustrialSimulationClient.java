package com.nexusworld.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.application.model.SimulationRecords.EngineResult;
import com.nexusworld.application.port.IndustrialSimulationClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpIndustrialSimulationClient implements IndustrialSimulationClient {
  private final RestClient restClient;

  @Autowired
  public HttpIndustrialSimulationClient(RestClient.Builder builder,
      @Value("${nexus.services.ai.base-url}") String baseUrl) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(30));
    restClient = builder.requestFactory(requestFactory).baseUrl(baseUrl).build();
  }

  HttpIndustrialSimulationClient(RestClient restClient) { this.restClient = restClient; }

  @Override
  public EngineResult execute(JsonNode request) {
    try {
      JsonNode response = restClient.post().uri("/api/v1/simulations/execute")
          .contentType(MediaType.APPLICATION_JSON).body(request.toString()).retrieve()
          .body(JsonNode.class);
      if (response == null || !response.path("resultHash").isTextual()
          || !response.path("snapshots").isArray() || !response.path("finalState").isArray()
          || !response.path("invariants").isObject()) {
        throw new IllegalStateException("AI service returned an invalid simulation response");
      }
      List<JsonNode> snapshots = new ArrayList<>();
      response.path("snapshots").forEach(snapshot -> snapshots.add(snapshot.deepCopy()));
      return new EngineResult(response.deepCopy(), response.path("resultHash").asText(),
          response.path("finalState").deepCopy(), response.path("invariants").deepCopy(), snapshots);
    } catch (RestClientResponseException exception) {
      String detail = exception.getResponseBodyAsString();
      if (detail.length() > 800) detail = detail.substring(0, 800);
      throw new AiServiceUnavailableException(
          "AI simulation request failed with HTTP " + exception.getStatusCode().value()
              + (detail.isBlank() ? "" : ": " + detail), exception);
    } catch (RestClientException | IllegalStateException exception) {
      throw new AiServiceUnavailableException(exception);
    }
  }
}
