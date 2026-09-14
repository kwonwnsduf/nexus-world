package com.nexusworld.infrastructure.ingestion.adapter;

import com.fasterxml.jackson.databind.*;
import com.nexusworld.application.ingestion.*;
import com.nexusworld.config.IngestionProperties;
import com.nexusworld.domain.ingestion.SourceSystem;
import com.nexusworld.infrastructure.ingestion.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WpiAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public WpiAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.WPI;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    return List.of(
        http.get(
            p.endpoints().wpi(),
            1,
            param(q, "version", "monthly"),
            Map.of("Accept", "text/csv"),
            1));
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    List<ParsedRecord> out = new ArrayList<>();
    for (Map<String, String> row :
        CsvRows.parse(Payloads.text(Payloads.decode(page.content(), page.contentEncoding())))) {
      String index =
          text(row, "World Port Index Number", "INDEX_NO", "WPI Number", "PORT_INDEX_NO");
      if (index == null) continue;
      String cc = text(row, "Country Code", "COUNTRY_CODE", "ISO_COUNTRY");
      JsonNode raw = object(row);
      out.add(
          record(
              "PORT",
              index,
              cc,
              cc == null ? null : "ISO-3166-1-alpha-2",
              index,
              "WPI",
              null,
              null,
              null,
              null,
              null,
              page.sourceVersion(),
              dimensions(
                  "name",
                  text(row, "Main Port Name", "PORT_NAME"),
                  "region",
                  text(row, "Region Name", "REGION_NAME"),
                  "latitude",
                  text(row, "Latitude", "LATITUDE"),
                  "longitude",
                  text(row, "Longitude", "LONGITUDE")),
              raw,
              raw));
    }
    return out;
  }
}
