package com.nexusworld.api.ontology;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateGraphEntityRequest(
        @NotBlank @Size(max = 48) String entityType,
        @NotBlank @Size(max = 200) String naturalKey,
        @NotBlank @Size(max = 240) String displayName,
        JsonNode attributes,
        Instant validFrom,
        Instant validTo) {}
