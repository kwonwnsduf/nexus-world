package com.nexusworld.api.ingestion;

import com.nexusworld.application.ingestion.IngestionException;
import java.time.Instant;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = IngestionController.class)
public class IngestionExceptionHandler {
  @ExceptionHandler({IngestionException.class, IllegalArgumentException.class})
  ResponseEntity<Problem> badRequest(RuntimeException e) {
    return ResponseEntity.badRequest()
        .body(new Problem("Ingestion failed", 400, e.getMessage(), Instant.now()));
  }

  record Problem(String title, int status, String detail, Instant timestamp) {}
}
