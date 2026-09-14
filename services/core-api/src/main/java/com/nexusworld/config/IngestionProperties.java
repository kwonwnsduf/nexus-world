package com.nexusworld.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nexus.ingestion")
public record IngestionProperties(
    @NotNull Duration connectTimeout,
    @NotNull Duration requestTimeout,
    @Min(1) @Max(8) int maxAttempts,
    @Min(1024) long maxPayloadBytes,
    String secUserAgent,
    String opendartApiKey,
    String unComtradeApiKey,
    String kosisApiKey,
    @Valid @NotNull Endpoints endpoints) {

  public record Endpoints(
      @NotNull URI sec,
      @NotNull URI opendart,
      @NotNull URI unComtrade,
      @NotNull URI worldBank,
      @NotNull URI usgs,
      @NotNull URI unlocode,
      @NotNull URI wpi,
      @NotNull URI hs,
      @NotNull URI isic,
      @NotNull URI unWpp,
      @NotNull URI ilostat,
      @NotNull URI kosis,
      @NotNull URI oecd) {}
}
