package com.nexusworld.api.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record StartIngestionRequest(@NotNull JsonNode parameters) {}
