package com.nexusworld.infrastructure.ingestion.adapter;

import com.fasterxml.jackson.databind.*;
import com.nexusworld.application.ingestion.*;
import com.nexusworld.config.IngestionProperties;
import com.nexusworld.domain.ingestion.SourceSystem;
import com.nexusworld.infrastructure.ingestion.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class UsgsAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public UsgsAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.USGS;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    Map<String, String> x = new LinkedHashMap<>();
    x.put("format", "geojson");
    x.put("starttime", required(q, "startTime"));
    x.put("endtime", required(q, "endTime"));
    x.put("minmagnitude", param(q, "minMagnitude", "4.5"));
    x.put("orderby", "time-asc");
    x.put("limit", param(q, "limit", "20000"));
    URI u = UriTools.build(p.endpoints().usgs(), "/query", x);
    return List.of(http.get(u, 1, "FDSN-event-1", Map.of("Accept", "application/geo+json"), 5));
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    List<ParsedRecord> out = new ArrayList<>();
    JsonNode root = json(page);
    String version = root.path("metadata").path("api").asText("1");
    for (JsonNode f : root.path("features")) {
      JsonNode props = f.path("properties"), coords = f.path("geometry").path("coordinates");
      String id = text(f, "id");
      Instant when = props.has("time") ? Instant.ofEpochMilli(props.path("time").asLong()) : null;
      out.add(
          record(
              "EARTHQUAKE",
              id,
              null,
              null,
              null,
              null,
              null,
              "Mw",
              when == null ? null : LocalDate.ofInstant(when, ZoneOffset.UTC),
              null,
              when,
              version,
              dimensions(
                  "network",
                  text(props, "net"),
                  "place",
                  text(props, "place"),
                  "longitude",
                  coords.path(0).asText(),
                  "latitude",
                  coords.path(1).asText(),
                  "depthKm",
                  coords.path(2).asText(),
                  "eventType",
                  text(props, "type")),
              value(props, "mag"),
              f));
    }
    return out;
  }
}
