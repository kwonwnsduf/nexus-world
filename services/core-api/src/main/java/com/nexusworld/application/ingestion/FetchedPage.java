package com.nexusworld.application.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record FetchedPage(
    String requestUri,
    int pageNumber,
    String mediaType,
    String contentEncoding,
    String sourceVersion,
    Instant retrievedAt,
    byte[] content,
    JsonNode responseMetadata) {
  public FetchedPage {
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}
