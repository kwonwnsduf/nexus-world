package com.nexusworld.api.ingestion;

import com.nexusworld.application.ingestion.*;
import com.nexusworld.domain.ingestion.SourceSystem;
import com.nexusworld.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/ingestions")
@PreAuthorize("hasRole('ADMIN')")
public class IngestionController {
  private final IngestionService service;

  public IngestionController(IngestionService service) {
    this.service = service;
  }

  @PostMapping("/{source}")
  public ResponseEntity<IngestionResult> start(
      @PathVariable SourceSystem source,
      @Valid @RequestBody StartIngestionRequest request,
      @AuthenticationPrincipal CustomUserDetails user) {
    IngestionResult result = service.ingest(source, request.parameters(), user.getUserId());
    return ResponseEntity.created(URI.create("/api/v1/admin/ingestions/" + result.runId()))
        .body(result);
  }

  @GetMapping("/{id}")
  public IngestionResult get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping
  public List<IngestionResult> recent() {
    return service.recent();
  }
}
