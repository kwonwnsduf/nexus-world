package com.nexusworld.api.simulation;

import com.nexusworld.application.simulation.*;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = SimulationController.class)
public class SimulationExceptionHandler {
  @ExceptionHandler(SimulationNotFoundException.class)
  ResponseEntity<Map<String, Object>> notFound(Exception exception) {
    return problem(HttpStatus.NOT_FOUND, "Not Found", exception.getMessage());
  }

  @ExceptionHandler(SimulationExecutionException.class)
  ResponseEntity<Map<String, Object>> unavailable(Exception exception) {
    return problem(HttpStatus.BAD_GATEWAY, "Simulation Engine Failure", exception.getMessage());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Map<String, Object>> conflict(Exception exception) {
    return problem(HttpStatus.CONFLICT, "Conflict",
        "The world, scenario, or branch conflicts with existing immutable state");
  }

  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
      ConstraintViolationException.class})
  ResponseEntity<Map<String, Object>> badRequest(Exception exception) {
    String detail = exception instanceof IllegalArgumentException
        ? exception.getMessage() : "The simulation request is invalid";
    return problem(HttpStatus.BAD_REQUEST, "Bad Request", detail);
  }

  private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String title, String detail) {
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(Map.of("title", title, "status", status.value(), "detail", detail));
  }
}
