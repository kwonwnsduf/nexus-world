package com.nexusworld.api.simulation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NaturalLanguageScenarioRequest(
    @NotBlank @Size(min = 3, max = 500) String query) {}
