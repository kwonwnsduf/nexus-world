package com.nexusworld.infrastructure.ingestion.adapter;

import com.fasterxml.jackson.databind.*;
import com.nexusworld.application.ingestion.*;
import com.nexusworld.config.IngestionProperties;
import com.nexusworld.domain.ingestion.SourceSystem;
import com.nexusworld.infrastructure.ingestion.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class IsicAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public IsicAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.ISIC;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    return List.of(
        http.get(
            p.endpoints().isic(),
            1,
            param(q, "version", "ISIC4"),
            Map.of("Accept", "text/csv,text/plain"),
            1));
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    String s = Payloads.text(Payloads.decode(page.content(), page.contentEncoding()));
    List<Map<String, String>> rows = CsvRows.parse(s);
    List<ParsedRecord> out = new ArrayList<>();
    for (Map<String, String> row : rows) {
      String code = text(row, "Code", "code", "ISIC4code", "ISIC Rev. 4 code");
      if (code == null) continue;
      JsonNode raw = object(row);
      String version = param(q, "version", "ISIC4");
      out.add(
          record(
              "ISIC_CLASSIFICATION",
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
                  "description",
                  text(row, "Description", "description", "Title"),
                  "level",
                  text(row, "Level", "level")),
              raw,
              raw));
    }
    return out;
  }
}
