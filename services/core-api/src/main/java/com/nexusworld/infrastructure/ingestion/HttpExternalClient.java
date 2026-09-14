package com.nexusworld.infrastructure.ingestion;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nexusworld.application.ingestion.FetchedPage;
import com.nexusworld.application.ingestion.IngestionException;
import com.nexusworld.config.IngestionProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class HttpExternalClient {
  private final HttpClient client;
  private final IngestionProperties properties;
  private final Clock clock;
  private final Map<String, Instant> nextAllowed = new ConcurrentHashMap<>();

  public HttpExternalClient(IngestionProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  public FetchedPage get(
      URI uri,
      int page,
      String sourceVersion,
      Map<String, String> headers,
      double requestsPerSecond) {
    RuntimeException last = null;
    for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
      throttle(uri.getHost(), requestsPerSecond);
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(uri)
              .timeout(properties.requestTimeout())
              .GET()
              .header("Accept-Encoding", "gzip");
      headers.forEach(builder::header);
      try {
        HttpResponse<byte[]> response =
            client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if ((status == 429 || status >= 500) && attempt < properties.maxAttempts()) {
          backoff(response, attempt);
          continue;
        }
        if (status < 200 || status >= 300)
          throw new IngestionException(
              "External request failed with HTTP " + status + " for " + UriTools.redact(uri));
        if (response.body().length > properties.maxPayloadBytes())
          throw new IngestionException(
              "External payload exceeds configured maximum for " + uri.getHost());
        var metadata = JsonNodeFactory.instance.objectNode().put("httpStatus", status);
        response.headers().firstValue("etag").ifPresent(v -> metadata.put("etag", v));
        response
            .headers()
            .firstValue("last-modified")
            .ifPresent(v -> metadata.put("lastModified", v));
        return new FetchedPage(
            UriTools.redact(uri),
            page,
            response.headers().firstValue("content-type").orElse("application/octet-stream"),
            response.headers().firstValue("content-encoding").orElse(null),
            sourceVersion,
            clock.instant(),
            response.body(),
            metadata);
      } catch (IOException e) {
        last = new IngestionException("External request I/O failure for " + uri.getHost(), e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IngestionException("External request interrupted", e);
      }
      if (attempt < properties.maxAttempts())
        sleep(Duration.ofMillis(250L * (1L << (attempt - 1))));
    }
    throw last == null ? new IngestionException("External request failed") : last;
  }

  private synchronized void throttle(String host, double rps) {
    if (rps <= 0) return;
    Instant now = clock.instant();
    Instant allowed = nextAllowed.get(host);
    if (allowed != null && allowed.isAfter(now)) sleep(Duration.between(now, allowed));
    nextAllowed.put(host, clock.instant().plusMillis(Math.max(1, (long) (1000 / rps))));
  }

  private void backoff(HttpResponse<?> response, int attempt) {
    long seconds =
        response
            .headers()
            .firstValue("retry-after")
            .flatMap(
                v -> {
                  try {
                    return java.util.Optional.of(Long.parseLong(v));
                  } catch (NumberFormatException e) {
                    return java.util.Optional.empty();
                  }
                })
            .orElse(1L << (attempt - 1));
    sleep(Duration.ofSeconds(Math.min(seconds, 30)));
  }

  private void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IngestionException("Retry interrupted", e);
    }
  }
}
