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
public class KosisAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public KosisAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.KOSIS;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    if (p.kosisApiKey() == null || p.kosisApiKey().isBlank())
      throw new IngestionException("KOSIS_API_KEY is required");
    Map<String, String> x = new LinkedHashMap<>();
    x.put("method", "getList");
    x.put("apiKey", p.kosisApiKey());
    x.put("format", "json");
    x.put("jsonVD", "Y");
    x.put("userStatsId", required(q, "userStatsId"));
    x.put("prdSe", param(q, "periodicity", "Y"));
    x.put("startPrdDe", required(q, "startPeriod"));
    x.put("endPrdDe", required(q, "endPeriod"));
    x.put("orgId", required(q, "orgId"));
    x.put("tblId", required(q, "tableId"));
    URI u = UriTools.build(p.endpoints().kosis(), "/Param/statisticsParameterData.do", x);
    return List.of(http.get(u, 1, "KOSIS-OpenAPI", Map.of("Accept", "application/json"), 2));
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    JsonNode root = json(page);
    if (root.isObject() && root.has("err"))
      throw new IngestionException("KOSIS error: " + root.path("errMsg").asText());
    List<ParsedRecord> out = new ArrayList<>();
    for (JsonNode n : root) {
      String table = text(n, "TBL_ID"), period = text(n, "PRD_DE"), item = text(n, "ITM_ID");
      String key =
          table
              + ":"
              + period
              + ":"
              + item
              + ":"
              + text(n, "C1")
              + ":"
              + text(n, "C2")
              + ":"
              + text(n, "C3");
      out.add(
          record(
              "KOREAN_STATISTIC",
              key,
              "KR",
              "ISO-3166-1-alpha-2",
              item,
              table,
              null,
              text(n, "UNIT_NM_ENG", "UNIT_NM"),
              date(period),
              null,
              null,
              text(n, "LST_CHN_DE"),
              dimensions(
                  "tableName",
                  text(n, "TBL_NM"),
                  "itemName",
                  text(n, "ITM_NM"),
                  "periodicity",
                  text(n, "PRD_SE"),
                  "c1",
                  text(n, "C1"),
                  "c1Name",
                  text(n, "C1_NM"),
                  "c2",
                  text(n, "C2"),
                  "c2Name",
                  text(n, "C2_NM")),
              value(n, "DT"),
              n));
    }
    return out;
  }
}
