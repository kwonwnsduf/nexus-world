package com.nexusworld.api.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateProvenanceLinkRequest(
        @NotBlank @Size(max = 80) String subjectType,
        @NotNull UUID subjectId,
        @NotNull @Size(max = 240) @Pattern(regexp = "^(/([^/~]|~[01])*)*$", message = "must be a JSON Pointer") String propertyPath,
        UUID evidenceId,
        UUID assumptionId,
        JsonNode transformation) {
    @AssertTrue(message = "exactly one of evidenceId or assumptionId is required")
    public boolean isExactlyOneOriginPresent() {
        return (evidenceId == null) != (assumptionId == null);
    }
}
