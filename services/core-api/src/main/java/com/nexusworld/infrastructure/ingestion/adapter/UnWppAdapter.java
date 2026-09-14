package com.nexusworld.infrastructure.ingestion.adapter;

import com.fasterxml.jackson.databind.*;
import com.nexusworld.application.ingestion.*;
import com.nexusworld.config.IngestionProperties;
import com.nexusworld.domain.ingestion.SourceSystem;
import com.nexusworld.infrastructure.ingestion.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class UnWppAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public UnWppAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.UN_WPP;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    return List.of(
        http.get(
            p.endpoints().unWpp(),
            1,
            param(q, "version", "WPP2024"),
            Map.of("Accept", "application/gzip,text/csv"),
            0.2));
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    List<ParsedRecord> out = new ArrayList<>();
    for (Map<String, String> row :
        CsvRows.parse(Payloads.text(Payloads.decode(page.content(), page.contentEncoding())))) {
      String loc = text(row, "Location code", "LocID", "SDMX_code"),
          iso3 = text(row, "ISO3_code"),
          year = text(row, "Year", "Time"),
          sex = text(row, "Sex"),
          age = text(row, "Age");
      if (loc == null || year == null) continue;
      String indicator = Optional.ofNullable(text(row, "Indicator", "VarID")).orElse("POP_TOTAL");
      JsonNode raw = object(row);
      String country = iso3 != null ? iso3 : (loc.matches("\\d{3}") ? loc : null),
          scheme = iso3 != null ? "ISO-3166-1-alpha-3" : (country == null ? null : "UN-M49");
      out.add(
          record(
              "POPULATION_TARGET",
              String.join(":", loc, year, String.valueOf(sex), String.valueOf(age), indicator),
              country,
              scheme,
              indicator,
              "WPP2024",
              null,
              Optional.ofNullable(text(row, "Unit")).orElse("thousands"),
              date(year),
              date(year) == null ? null : date(year).plusYears(1).minusDays(1),
              null,
              page.sourceVersion(),
              dimensions(
                  "locationId",
                  loc,
                  "location",
                  text(row, "Location"),
                  "iso2",
                  text(row, "ISO2_code"),
                  "sex",
                  sex,
                  "age",
                  age,
                  "variant",
                  text(row, "Variant")),
              value(raw, "Value", "PopTotal", "TPopulation1July", "MidPeriodPop"),
              raw));
    }
    return out;
  }
}
