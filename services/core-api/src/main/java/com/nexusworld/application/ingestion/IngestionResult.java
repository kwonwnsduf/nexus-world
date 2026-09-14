package com.nexusworld.application.ingestion;

import com.nexusworld.domain.ingestion.*;
import java.time.Instant;
import java.util.UUID;

public record IngestionResult(
    UUID runId,
    SourceSystem source,
    IngestionStatus status,
    Instant startedAt,
    Instant completedAt,
    int pagesFetched,
    int rawRecords,
    int normalizedRecords,
    int rejectedRecords,
    String errorMessage) {}
