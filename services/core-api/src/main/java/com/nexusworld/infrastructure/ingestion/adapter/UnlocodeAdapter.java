package com.nexusworld.infrastructure.ingestion.adapter;

import com.fasterxml.jackson.databind.*;
import com.nexusworld.application.ingestion.*;
import com.nexusworld.config.IngestionProperties;
import com.nexusworld.domain.ingestion.SourceSystem;
import com.nexusworld.infrastructure.ingestion.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class UnlocodeAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public UnlocodeAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.UNLOCODE;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    return List.of(
        http.get(
            p.endpoints().unlocode(),
            1,
            param(q, "version", "2025-1"),
            Map.of("Accept", "application/zip,text/csv"),
            1));
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    List<ParsedRecord> out = new ArrayList<>();
    Map<String, byte[]> files = Payloads.unzip(page.content());
    for (var file : files.entrySet()) {
      if (!file.getKey().toLowerCase(Locale.ROOT).endsWith(".csv")) continue;
      String s = Payloads.text(file.getValue());
      String first = s.lines().findFirst().orElse("").toLowerCase(Locale.ROOT);
      if (!first.contains("change") && !first.contains("country"))
        s =
            "Change,Country,Location,Name,NameWoDiacritics,Subdivision,Function,"
                + "Status,Date,IATA,Coordinates,Remarks\n"
                + s;
      for (Map<String, String> row : CsvRows.parse(s)) {
        String cc = text(row, "Country", "country"), loc = text(row, "Location", "location");
        if (cc == null || loc == null) continue;
        String key = cc + loc;
        JsonNode raw = object(row);
        out.add(
            record(
                "LOCATION_CODE",
                key,
                cc,
                "ISO-3166-1-alpha-2",
                key,
                "UNLOCODE",
                null,
                null,
                date(text(row, "Date", "date")),
                null,
                null,
                page.sourceVersion(),
                dimensions(
                    "name",
                    text(row, "Name", "name"),
                    "subdivision",
                    text(row, "Subdivision", "SubDiv"),
                    "function",
                    text(row, "Function"),
                    "status",
                    text(row, "Status"),
                    "coordinates",
                    text(row, "Coordinates")),
                raw,
                raw));
      }
    }
    return out;
  }
}
