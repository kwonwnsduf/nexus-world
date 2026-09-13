package com.nexusworld.api.ontology;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateGraphRelationshipRequest(
        @NotBlank @Size(max = 48) String relationshipType,
        @NotNull UUID sourceEntityId,
        @NotNull UUID targetEntityId,
        JsonNode attributes,
        Instant validFrom,
        Instant validTo) {}
