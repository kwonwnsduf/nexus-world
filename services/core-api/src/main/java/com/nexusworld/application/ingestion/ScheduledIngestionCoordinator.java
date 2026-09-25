package com.nexusworld.application.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.nexusworld.config.IngestionScheduleProperties;
import com.nexusworld.domain.ingestion.SourceSystem;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "nexus.ingestion.schedule", name = "enabled", havingValue = "true")
public class ScheduledIngestionCoordinator {
  private static final Logger log = LoggerFactory.getLogger(ScheduledIngestionCoordinator.class);

  private final IngestionService ingestion;
  private final IngestionScheduleProperties properties;
  private final ObjectMapper json;
  private final Clock clock;
  private final Map<SourceSystem, Instant> nextRuns = new EnumMap<>(SourceSystem.class);
  private final AtomicBoolean polling = new AtomicBoolean();

  public ScheduledIngestionCoordinator(IngestionService ingestion,
      IngestionScheduleProperties properties, ObjectMapper json, Clock clock) {
    this.ingestion = ingestion;
    this.properties = properties;
    this.json = json;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${nexus.ingestion.schedule.poll-delay:PT1M}")
  public void poll() {
    if (!polling.compareAndSet(false, true)) return;
    try {
      UUID actor = requiredActor();
      Instant now = clock.instant();
      configuredSources().forEach((name, schedule) -> runIfDue(name, schedule, actor, now));
    } finally {
      polling.set(false);
    }
  }

  private Map<String, IngestionScheduleProperties.SourceSchedule> configuredSources() {
    Map<String, IngestionScheduleProperties.SourceSchedule> configured =
        new LinkedHashMap<>(properties.sources());
    try {
      configured.putAll(json.readValue(properties.sourcesJson(), new TypeReference<>() {}));
      return configured;
    } catch (Exception exception) {
      throw new IllegalStateException("INGESTION_SCHEDULE_SOURCES_JSON is invalid", exception);
    }
  }

  private void runIfDue(String name, IngestionScheduleProperties.SourceSchedule schedule,
      UUID actor, Instant now) {
    if (!schedule.enabled()) return;
    SourceSystem source;
    CronExpression cron;
    try {
      source = SourceSystem.valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
      cron = CronExpression.parse(schedule.cron());
    } catch (RuntimeException exception) {
      log.error("Invalid ingestion schedule for {}", name, exception);
      return;
    }
    Instant next = nextRuns.computeIfAbsent(source, ignored -> previousOrNow(cron, now));
    if (now.isBefore(next)) return;
    try {
      for (JsonNode parameters : requests(schedule)) {
        try {
          ingestion.ingest(source, parameters, actor);
        } catch (Exception exception) {
          log.error("Scheduled ingestion failed for {} request {}", source,
              parameters.toString(), exception);
        }
      }
    } catch (Exception exception) {
      log.error("Scheduled ingestion failed for {}", source, exception);
    } finally {
      ZonedDateTime following = cron.next(ZonedDateTime.ofInstant(now, clock.getZone()));
      nextRuns.put(source, following == null ? Instant.MAX : following.toInstant());
    }
  }

  private List<JsonNode> requests(IngestionScheduleProperties.SourceSchedule schedule) {
    if (!schedule.requests().isEmpty()) {
      return schedule.requests().stream()
          .map(request -> (JsonNode) request.deepCopy()).toList();
    }
    try {
      return List.of(json.readTree(blankToObject(schedule.parametersJson())));
    } catch (Exception exception) {
      throw new IllegalStateException("Scheduled ingestion parameters are invalid JSON", exception);
    }
  }

  private Instant previousOrNow(CronExpression cron, Instant now) {
    ZonedDateTime following = cron.next(ZonedDateTime.ofInstant(now.minusSeconds(1), clock.getZone()));
    return following == null ? Instant.MAX : following.toInstant();
  }

  private UUID requiredActor() {
    if (properties.actorId() == null || properties.actorId().isBlank()) {
      throw new IllegalStateException("INGESTION_SCHEDULE_ACTOR_ID is required when scheduling is enabled");
    }
    return UUID.fromString(properties.actorId());
  }

  private String blankToObject(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }
}
