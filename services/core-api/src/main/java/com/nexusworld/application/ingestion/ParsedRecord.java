package com.nexusworld.application.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;

public record ParsedRecord(
    String recordType,
    String naturalKey,
    String countryCode,
    String countryCodeScheme,
    String classificationCode,
    String classificationVersion,
    String currencyCode,
    String unitCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    Instant observedAt,
    String dataVersion,
    JsonNode dimensions,
    JsonNode value,
    JsonNode sourceRecord) {}
