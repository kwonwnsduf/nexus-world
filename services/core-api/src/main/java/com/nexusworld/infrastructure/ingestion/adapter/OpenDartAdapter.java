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
public class OpenDartAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public OpenDartAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.OPENDART;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    if (p.opendartApiKey() == null || p.opendartApiKey().isBlank())
      throw new IngestionException("OPENDART_API_KEY is required");
    int maxPages = boundedInt(q, "maxPages", 100, 1, 1000);
    int total = 1;
    List<FetchedPage> pages = new ArrayList<>();
    for (int page = 1; page <= Math.min(total, maxPages); page++) {
      Map<String, String> x = new LinkedHashMap<>();
      x.put("crtfc_key", p.opendartApiKey());
      x.put("bgn_de", required(q, "startDate"));
      x.put("end_de", required(q, "endDate"));
      x.put("corp_code", text(q, "corpCode"));
      x.put("page_no", String.valueOf(page));
      x.put("page_count", param(q, "pageSize", "100"));
      URI u = UriTools.build(p.endpoints().opendart(), "/api/list.json", x);
      FetchedPage f = http.get(u, page, "OpenDART-v1", Map.of("Accept", "application/json"), 4);
      pages.add(f);
      JsonNode root = json(f);
      String status = root.path("status").asText();
      if (!status.equals("000") && !status.equals("013"))
        throw new IngestionException(
            "OpenDART error " + status + ": " + root.path("message").asText());
      total = Math.max(1, root.path("total_page").asInt(1));
    }
    return pages;
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
    for (JsonNode n : json(page).path("list")) {
      String receipt = text(n, "rcept_no");
      String filed = text(n, "rcept_dt");
      out.add(
          record(
              "OPENDART_FILING",
              text(n, "corp_code") + ":" + receipt,
              "KR",
              "ISO-3166-1-alpha-2",
              text(n, "report_nm"),
              "OpenDART",
              null,
              null,
              date(filed),
              date(filed),
              null,
              page.sourceVersion(),
              dimensions(
                  "corpCode",
                  text(n, "corp_code"),
                  "corpName",
                  text(n, "corp_name"),
                  "market",
                  text(n, "corp_cls")),
              n,
              n));
    }
    return out;
  }
}
