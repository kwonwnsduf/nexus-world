package com.nexusworld.api.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.evidence.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateSourceRequest(
        @NotBlank @Size(max = 160) String sourceKey,
        @NotNull SourceType sourceType,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 240) String publisher,
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9+.-]*:.*$", message = "must be an absolute URI") String canonicalUri,
        @Size(max = 120) String sourceVersion,
        @Size(max = 160) String license,
        Instant publishedAt,
        @NotNull Instant retrievedAt,
        @Pattern(regexp = "^[0-9a-f]{64}$", message = "must be a lowercase SHA-256 digest") String contentSha256,
        JsonNode metadata) {}
