package com.nexusworld.infrastructure.ingestion.adapter;

import com.fasterxml.jackson.databind.*;
import com.nexusworld.application.ingestion.*;
import com.nexusworld.config.IngestionProperties;
import com.nexusworld.domain.ingestion.SourceSystem;
import com.nexusworld.infrastructure.ingestion.*;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WorldBankAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public WorldBankAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.WORLD_BANK;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    String country = required(q, "country"), indicator = required(q, "indicator");
    requirePathSegment(country, "country", "[A-Za-z0-9;_-]+");
    requirePathSegment(indicator, "indicator", "[A-Za-z0-9._-]+");
    int maxPages = boundedInt(q, "maxPages", 100, 1, 1000);
    List<FetchedPage> out = new ArrayList<>();
    int total = 1;
    for (int page = 1; page <= Math.min(total, maxPages); page++) {
      Map<String, String> x = new LinkedHashMap<>();
      x.put("format", "json");
      x.put("page", String.valueOf(page));
      x.put("per_page", param(q, "pageSize", "1000"));
      x.put("date", text(q, "date"));
      URI u =
          UriTools.build(
              p.endpoints().worldBank(), "/country/" + country + "/indicator/" + indicator, x);
      FetchedPage f =
          http.get(u, page, "World-Bank-API-v2", Map.of("Accept", "application/json"), 5);
      out.add(f);
      JsonNode root = json(f);
      if (!root.isArray() || root.size() < 2)
        throw new IngestionException("Unexpected World Bank response");
      total = root.path(0).path("pages").asInt(1);
    }
    return out;
  }

  private void requirePathSegment(String value, String name, String pattern) {
    if (!value.matches(pattern)) {
      throw new IngestionException(name + " contains unsupported characters");
    }
  }

  private int boundedInt(JsonNode query, String name, int defaultValue, int min, int max) {
    int value = query.path(name).asInt(defaultValue);
    if (value < min || value > max) {
      throw new IngestionException(name + " must be between " + min + " and " + max);
    }
    return value;
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    List<ParsedRecord> out = new ArrayList<>();
    JsonNode rows = json(page).path(1);
    for (JsonNode n : rows) {
      if (n.path("value").isNull()) continue;
      String iso3 = text(n, "countryiso3code"),
          year = text(n, "date"),
          indicator = n.path("indicator").path("id").asText();
      out.add(
          record(
              "MACRO_INDICATOR",
              iso3 + ":" + indicator + ":" + year,
              iso3,
              "ISO-3166-1-alpha-3",
              indicator,
              "World-Bank-v2",
              null,
              text(n, "unit"),
              date(year),
              date(year) == null ? null : date(year).plusYears(1).minusDays(1),
              null,
              text(n, "lastupdated"),
              dimensions(
                  "countryName",
                  n.path("country").path("value").asText(),
                  "decimal",
                  text(n, "decimal")),
              n.path("value"),
              n));
    }
    return out;
  }
}
