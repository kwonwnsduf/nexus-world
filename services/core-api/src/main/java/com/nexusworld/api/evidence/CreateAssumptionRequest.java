package com.nexusworld.api.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.evidence.AssumptionStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record CreateAssumptionRequest(
        @NotBlank @Size(max = 160) String assumptionKey,
        @NotBlank @Size(max = 80) String category,
        @NotBlank String statement,
        @NotBlank String rationale,
        JsonNode assumedValue,
        @Size(max = 80) String unit,
        @NotNull AssumptionStatus status,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
        Instant validFrom,
        Instant validTo) {
    @AssertTrue(message = "validTo requires validFrom and must not precede it")
    public boolean isValidPeriod() {
        return validTo == null || (validFrom != null && !validTo.isBefore(validFrom));
    }
}
