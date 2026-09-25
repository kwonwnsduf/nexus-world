package com.nexusworld.infrastructure.ai;

import com.nexusworld.application.model.ScenarioWorkflowRecords.ScenarioCandidate;
import com.nexusworld.application.port.ScenarioInterpreter;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpScenarioInterpreter implements ScenarioInterpreter {
  private final RestClient client;

  @Autowired
  public HttpScenarioInterpreter(RestClient.Builder builder,
      @Value("${nexus.services.ai.base-url}") String baseUrl) {
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(Duration.ofSeconds(5));
    requests.setReadTimeout(Duration.ofSeconds(40));
    this.client = builder.requestFactory(requests).baseUrl(baseUrl).build();
  }

  HttpScenarioInterpreter(RestClient client) {
    this.client = client;
  }

  @Override
  public ScenarioCandidate interpret(String query) {
    try {
      ScenarioCandidate value = client.post().uri("/api/v1/scenarios/interpret")
          .body(Map.of("query", query)).retrieve().body(ScenarioCandidate.class);
      if (value == null || !"v2".equals(value.contractVersion()) || value.target() == null
          || value.target().isBlank()) {
        throw new IllegalStateException("AI service returned an invalid scenario mapping");
      }
      return value;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
        throw new IllegalArgumentException(
            "자연어 입력을 구조화된 시나리오로 안전하게 변환하지 못했습니다");
      }
      throw new AiServiceUnavailableException(exception);
    } catch (RestClientException | IllegalStateException exception) {
      throw new AiServiceUnavailableException(exception);
    }
  }
}
