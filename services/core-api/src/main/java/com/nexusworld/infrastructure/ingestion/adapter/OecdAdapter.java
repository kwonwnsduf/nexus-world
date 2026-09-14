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
public class OecdAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public OecdAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.OECD;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    String flow = required(q, "flowRef"), key = param(q, "key", "all");
    if (!flow.matches("[A-Za-z0-9_.@,-]+") || !key.matches("[A-Za-z0-9_.+~-]+"))
      throw new IngestionException("Invalid OECD SDMX flowRef or key");
    Map<String, String> x = new LinkedHashMap<>();
    x.put("startPeriod", text(q, "startPeriod"));
    x.put("endPeriod", text(q, "endPeriod"));
    x.put("dimensionAtObservation", "AllDimensions");
    x.put("format", "csvfile");
    URI u = UriTools.build(p.endpoints().oecd(), "/data/" + flow + "/" + key, x);
    return List.of(http.get(u, 1, "OECD-SDMX", Map.of("Accept", "text/csv"), 2));
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    List<ParsedRecord> out = new ArrayList<>();
    for (Map<String, String> row :
        CsvRows.parse(Payloads.text(Payloads.decode(page.content(), page.contentEncoding())))) {
      String area = text(row, "REF_AREA", "Reference area"),
          period = text(row, "TIME_PERIOD", "Time period"),
          measure = text(row, "MEASURE", "Measure"),
          unit = text(row, "UNIT_MEASURE", "Unit of measure");
      if (period == null) continue;
      JsonNode raw = object(row);
      String
          key =
              String.join(
                  ":",
                  String.valueOf(area),
                  String.valueOf(measure),
                  period,
                  String.valueOf(text(row, "FREQ")),
                  String.valueOf(text(row, "ACTIVITY")),
                  String.valueOf(text(row, "SEX"))),
          country = area != null && area.matches("[A-Z]{3}") ? area : null;
      out.add(
          record(
              "OECD_STATISTIC",
              key,
              country,
              country == null ? null : "ISO-3166-1-alpha-3",
              measure,
              text(row, "DATAFLOW"),
              text(row, "CURRENCY"),
              unit,
              date(period.replaceAll("-.*$", "")),
              null,
              null,
              text(row, "VERSION", "LAST_UPDATED"),
              dimensions(
                  "refArea",
                  area,
                  "frequency",
                  text(row, "FREQ"),
                  "subject",
                  text(row, "SUBJECT"),
                  "activity",
                  text(row, "ACTIVITY"),
                  "sex",
                  text(row, "SEX"),
                  "priceBase",
                  text(row, "PRICE_BASE")),
              value(raw, "OBS_VALUE", "Observation value"),
              raw));
    }
    return out;
  }
}
