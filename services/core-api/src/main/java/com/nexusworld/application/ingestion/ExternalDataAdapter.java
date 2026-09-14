package com.nexusworld.application.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.ingestion.SourceSystem;
import java.util.List;

public interface ExternalDataAdapter {
  SourceSystem source();

  List<FetchedPage> fetch(JsonNode parameters);

  List<ParsedRecord> parse(FetchedPage page, JsonNode parameters);
}
