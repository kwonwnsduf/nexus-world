package com.nexusworld.infrastructure.ingestion.adapter;

import com.fasterxml.jackson.databind.*;
import com.nexusworld.application.ingestion.*;
import com.nexusworld.config.IngestionProperties;
import com.nexusworld.domain.ingestion.SourceSystem;
import com.nexusworld.infrastructure.ingestion.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class HsAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public HsAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.HS;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    return List.of(
        http.get(
            p.endpoints().hs(),
            1,
            param(q, "version", "HS2022"),
            Map.of("Accept", "application/json"),
            1));
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    JsonNode root = json(page);
    JsonNode rows = root.isArray() ? root : root.path("results");
    List<ParsedRecord> out = new ArrayList<>();
    for (JsonNode n : rows) {
      String code = text(n, "id", "code", "cmdCode");
      if (code == null) continue;
      String version = param(q, "version", "HS2022");
      out.add(
          record(
              "HS_CLASSIFICATION",
              version + ":" + code,
              null,
              null,
              code,
              version,
              null,
              null,
              null,
              null,
              null,
              version,
              dimensions(
                  "description", text(n, "text", "description"), "parent", text(n, "parent")),
              n,
              n));
    }
    return out;
  }
}
