package com.nexusworld.api.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.evidence.EvidenceType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateEvidenceRequest(
        @NotNull UUID sourceId,
        @NotNull EvidenceType evidenceType,
        @NotBlank String claimText,
        JsonNode locator,
        String excerpt,
        JsonNode measuredValue,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
        Instant observedAt,
        Instant validFrom,
        Instant validTo) {
    @AssertTrue(message = "excerpt or measuredValue is required")
    public boolean isPayloadPresent() {
        return (excerpt != null && !excerpt.isBlank()) || measuredValue != null;
    }

    @AssertTrue(message = "validTo requires validFrom and must not precede it")
    public boolean isValidPeriod() {
        return validTo == null || (validFrom != null && !validTo.isBefore(validFrom));
    }
}
