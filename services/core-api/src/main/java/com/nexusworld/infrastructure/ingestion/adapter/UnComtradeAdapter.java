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
public class UnComtradeAdapter extends AdapterSupport {
  private final HttpExternalClient http;
  private final IngestionProperties p;

  public UnComtradeAdapter(ObjectMapper m, HttpExternalClient h, IngestionProperties p) {
    super(m);
    http = h;
    this.p = p;
  }

  public SourceSystem source() {
    return SourceSystem.UN_COMTRADE;
  }

  public List<FetchedPage> fetch(JsonNode q) {
    if (p.unComtradeApiKey() == null || p.unComtradeApiKey().isBlank())
      throw new IngestionException("UN_COMTRADE_API_KEY is required");
    String classification = param(q, "classification", "HS");
    if (!classification.matches("[A-Z0-9]+")) {
      throw new IngestionException("classification contains unsupported characters");
    }
    Map<String, String> x = new LinkedHashMap<>();
    x.put("reporterCode", required(q, "reporterCode"));
    x.put("period", required(q, "period"));
    x.put("partnerCode", param(q, "partnerCode", "0"));
    x.put("flowCode", param(q, "flowCode", "X"));
    x.put("cmdCode", param(q, "cmdCode", "TOTAL"));
    x.put("maxRecords", param(q, "maxRecords", "100000"));
    x.put("includeDesc", "true");
    URI u = UriTools.build(p.endpoints().unComtrade(), "/data/v1/get/C/A/" + classification, x);
    return List.of(http.get(u, 1, "UN-Comtrade-v1",
        Map.of("Accept", "application/json",
            "Ocp-Apim-Subscription-Key", p.unComtradeApiKey()), 1));
  }

  public List<ParsedRecord> parse(FetchedPage page, JsonNode q) {
    List<ParsedRecord> out = new ArrayList<>();
    for (JsonNode n : json(page).path("data")) {
      String reporter = text(n, "reporterCode");
      String partner = text(n, "partnerCode");
      String period = text(n, "period");
      String flow = text(n, "flowCode");
      String cmd = text(n, "cmdCode");
      String classification = text(n, "classificationCode", "clCode");
      String key = String.join(":", reporter, partner, period, flow, cmd);
      out.add(
          record(
              "TRADE_FLOW",
              key,
              reporter,
              "UN-M49",
              cmd,
              classification,
              "USD",
              text(n, "qtyUnitAbbr", "qtyUnitCode"),
              date(period),
              date(period) == null ? null : date(period).plusYears(1).minusDays(1),
              null,
              text(n, "isReported", "isAggregate"),
              dimensions(
                  "reporterName",
                  text(n, "reporterDesc", "reporterISO"),
                  "reporterIso",
                  text(n, "reporterISO"),
                  "partnerCode",
                  partner,
                  "partnerName",
                  text(n, "partnerDesc", "partnerISO"),
                  "partnerIso",
                  text(n, "partnerISO"),
                  "flowCode",
                  flow,
                  "customsCode",
                  text(n, "customsCode"),
                  "motCode",
                  text(n, "motCode")),
              value(n, "primaryValue", "netWgt", "qty"),
              n));
    }
    return out;
  }
}
