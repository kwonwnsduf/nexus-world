package com.nexusworld.infrastructure.ingestion.adapter;

import com.fasterxml.jackson.databind.*;
import com.nexusworld.application.ingestion.*;
import com.nexusworld.config.IngestionProperties;
import com.nexusworld.domain.ingestion.SourceSystem;
import com.nexusworld.infrastructure.ingestion.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class SecAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public SecAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.SEC;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    String cik = required(q, "cik").replaceAll("\\D", "");
    if (cik.isBlank() || cik.length() > 10)
      throw new IngestionException("SEC cik must contain 1-10 digits");
    String ua = p.secUserAgent();
    if (ua == null || ua.isBlank())
      throw new IngestionException(
          "SEC_USER_AGENT must identify the application and contact email");
    String padded = String.format("%010d", Long.parseLong(cik));
    Map<String, String> headers = Map.of("User-Agent", ua, "Accept", "application/json");
    List<FetchedPage> pages = new ArrayList<>();
    pages.add(
        http.get(
            UriTools.build(p.endpoints().sec(), "/submissions/CIK" + padded + ".json", Map.of()),
            1,
            "EDGAR-submissions-v1",
            headers,
            8));
    if (q.path("includeCompanyFacts").asBoolean(true))
      pages.add(
          http.get(
              UriTools.build(
                  p.endpoints().sec(), "/api/xbrl/companyfacts/CIK" + padded + ".json", Map.of()),
              2,
              "EDGAR-companyfacts-v1",
              headers,
              8));
    return pages;
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    JsonNode root = json(page);
    return root.has("facts") ? parseFacts(root, page) : parseFilings(root, page);
  }

  private List<ParsedRecord> parseFilings(JsonNode root, FetchedPage page) {
    JsonNode recent = root.path("filings").path("recent");
    List<ParsedRecord> out = new ArrayList<>();
    JsonNode accessions = recent.path("accessionNumber");
    for (int i = 0; i < accessions.size(); i++) {
      String accession = accessions.path(i).asText(),
          cik = root.path("cik").asText(),
          filed = recent.path("filingDate").path(i).asText();
      JsonNode raw =
          mapper
              .createObjectNode()
              .put("accessionNumber", accession)
              .put("form", recent.path("form").path(i).asText())
              .put("filingDate", filed)
              .put("reportDate", recent.path("reportDate").path(i).asText())
              .put("primaryDocument", recent.path("primaryDocument").path(i).asText());
      out.add(
          record(
              "SEC_FILING",
              cik + ":" + accession,
              "US",
              "ISO-3166-1-alpha-2",
              recent.path("form").path(i).asText(),
              "EDGAR",
              null,
              null,
              date(filed),
              date(filed),
              null,
              page.sourceVersion(),
              dimensions("cik", cik, "companyName", root.path("name").asText(),
                  "sic", text(root, "sic"), "sicDescription", text(root, "sicDescription"),
                  "tickers", root.path("tickers").toString()),
              raw,
              raw));
    }
    return out;
  }

  private List<ParsedRecord> parseFacts(JsonNode root, FetchedPage page) {
    List<ParsedRecord> out = new ArrayList<>();
    String cik = root.path("cik").asText();
    for (Map.Entry<String, JsonNode> taxonomy : root.path("facts").properties()) {
      for (Map.Entry<String, JsonNode> concept : taxonomy.getValue().properties()) {
        JsonNode definition = concept.getValue();
        for (Map.Entry<String, JsonNode> unit : definition.path("units").properties()) {
          for (JsonNode n : unit.getValue()) {
            String accession = text(n, "accn"),
                start = text(n, "start"),
                end = text(n, "end"),
                frame = text(n, "frame");
            String key =
                String.join(
                    ":",
                    cik,
                    taxonomy.getKey(),
                    concept.getKey(),
                    unit.getKey(),
                    String.valueOf(accession),
                    String.valueOf(start),
                    String.valueOf(end),
                    String.valueOf(frame));
            out.add(
                record(
                    "SEC_XBRL_FACT",
                    key,
                    "US",
                    "ISO-3166-1-alpha-2",
                    concept.getKey(),
                    taxonomy.getKey(),
                    unit.getKey().matches("[A-Z]{3}") ? unit.getKey() : null,
                    unit.getKey(),
                    date(start == null ? end : start),
                    date(end),
                    null,
                    page.sourceVersion(),
                    dimensions(
                        "cik",
                        cik,
                        "entityName",
                        root.path("entityName").asText(),
                        "label",
                        text(definition, "label"),
                        "description",
                        text(definition, "description"),
                        "form",
                        text(n, "form"),
                        "accession",
                        accession,
                        "filed",
                        text(n, "filed"),
                        "frame",
                        frame,
                        "fiscalYear",
                        text(n, "fy"),
                        "fiscalPeriod",
                        text(n, "fp")),
                    value(n, "val"),
                    n));
          }
        }
      }
    }
    return out;
  }
}
