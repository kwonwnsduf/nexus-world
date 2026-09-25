package com.nexusworld.config;

import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.ingestion.schedule")
public record IngestionScheduleProperties(
    boolean enabled,
    String actorId,
    Duration pollDelay,
    String sourcesJson,
    Map<String, SourceSchedule> sources) {

  public IngestionScheduleProperties {
    pollDelay = pollDelay == null ? Duration.ofMinutes(1) : pollDelay;
    sourcesJson = sourcesJson == null || sourcesJson.isBlank() ? "{}" : sourcesJson;
    sources = sources == null ? Map.of() : Map.copyOf(sources);
  }

  public record SourceSchedule(boolean enabled, String cron, String parametersJson,
      List<JsonNode> requests) {
    public SourceSchedule {
      requests = requests == null ? List.of() : List.copyOf(requests);
    }
  }
}
