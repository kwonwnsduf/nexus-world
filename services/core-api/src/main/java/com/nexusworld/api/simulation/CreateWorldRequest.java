package com.nexusworld.api.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorldRequest(
    @NotBlank @Size(max = 160) String name,
    @NotNull JsonNode state) {}
