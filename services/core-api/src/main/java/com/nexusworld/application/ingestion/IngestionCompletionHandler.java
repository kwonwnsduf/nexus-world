package com.nexusworld.application.ingestion;

import com.nexusworld.domain.ingestion.SourceSystem;
import java.util.UUID;

public interface IngestionCompletionHandler {
  void onCompleted(UUID runId, SourceSystem source, int normalizedRecords, UUID actor);
}
