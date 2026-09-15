package com.nexusworld.api.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public record ResolveGraphEntityRequest(
    @NotBlank @Size(max=48) String entityType,
    @NotBlank @Size(max=200) String naturalKey,
    @NotBlank @Size(max=240) String displayName,
    JsonNode attributes,
    @Valid @Size(max=16) List<Identifier> identifiers,
    @Size(max=32) String sourceSystem,
    Instant validFrom,
    Instant validTo) {
  public record Identifier(@NotBlank @Size(max=48) String scheme,
      @NotBlank @Size(max=512) String value) {}
}
