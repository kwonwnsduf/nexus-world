package com.nexusworld.api.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ValidateActionRequest(
        @NotBlank @Size(max = 64) String actionCode,
        @NotBlank @Size(max = 48) String actorType,
        @NotBlank @Size(max = 48) String targetType,
        @NotNull JsonNode parameters) {}
