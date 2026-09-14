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
public class IlostatAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public IlostatAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.ILOSTAT;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    String dataset = required(q, "dataset");
    if (!dataset.matches("[A-Z0-9_]+"))
      throw new IngestionException("Invalid ILOSTAT dataset code");
    URI u =
        URI.create(
            p.endpoints().ilostat().toString().replaceAll("/$", "") + "/" + dataset + ".csv.gz");
    return List.of(
        http.get(u, 1, "ILOSTAT-bulk", Map.of("Accept", "application/gzip,text/csv"), 1));
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    List<ParsedRecord> out = new ArrayList<>();
    for (Map<String, String> row :
        CsvRows.parse(Payloads.text(Payloads.decode(page.content(), page.contentEncoding())))) {
      String area = text(row, "ref_area"),
          indicator = text(row, "indicator"),
          time = text(row, "time");
      if (area == null || indicator == null || time == null) continue;
      JsonNode raw = object(row);
      String
          key =
              area
                  + ":"
                  + indicator
                  + ":"
                  + time
                  + ":"
                  + text(row, "sex")
                  + ":"
                  + text(row, "classif1")
                  + ":"
                  + text(row, "classif2"),
          country = area.matches("[A-Z]{3}") ? area : null;
      out.add(
          record(
              "LABOR_INDICATOR",
              key,
              country,
              country == null ? null : "ISO-3166-1-alpha-3",
              indicator,
              text(row, "source"),
              null,
              text(row, "unit_measure"),
              date(time.replaceAll("[^0-9].*", "")),
              null,
              null,
              text(row, "last_status"),
              dimensions(
                  "refArea",
                  area,
                  "frequency",
                  text(row, "freq"),
                  "sex",
                  text(row, "sex"),
                  "classif1",
                  text(row, "classif1"),
                  "classif2",
                  text(row, "classif2"),
                  "obsStatus",
                  text(row, "obs_status")),
              value(raw, "obs_value"),
              raw));
    }
    return out;
  }
}
