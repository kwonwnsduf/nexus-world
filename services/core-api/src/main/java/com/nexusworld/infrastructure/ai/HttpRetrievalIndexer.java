package com.nexusworld.infrastructure.ai;

import com.nexusworld.application.port.RetrievalIndexer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpRetrievalIndexer implements RetrievalIndexer {
  private final RestClient client;

  public HttpRetrievalIndexer(RestClient.Builder builder,
      @Value("${nexus.services.ai.base-url}") String baseUrl) {
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(Duration.ofSeconds(5));
    requests.setReadTimeout(Duration.ofSeconds(30));
    client = builder.requestFactory(requests).baseUrl(baseUrl).build();
  }

  @Override
  public void index(String documentId, String title, String content, String sourceUri,
      UUID dataSourceId, UUID evidenceId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("documentId", documentId);
    body.put("title", title);
    body.put("content", content);
    body.put("sourceUri", sourceUri);
    body.put("dataSourceId", dataSourceId);
    body.put("evidenceId", evidenceId);
    client.post().uri("/api/v1/retrieval/documents").body(body).retrieve().toBodilessEntity();
  }
}
