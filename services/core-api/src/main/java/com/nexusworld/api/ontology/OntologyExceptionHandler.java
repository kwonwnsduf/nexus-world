package com.nexusworld.api.ontology;

import com.nexusworld.application.ontology.*;
import com.nexusworld.application.graph.GraphStoreUnavailableException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=OntologyController.class)
public class OntologyExceptionHandler {
    @ExceptionHandler(OntologyNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(Exception exception) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", exception.getMessage());
    }

    @ExceptionHandler({OntologyConflictException.class,DataIntegrityViolationException.class})
    ResponseEntity<Map<String, Object>> conflict(Exception exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Conflict",
                "The graph resource conflicts with the ontology or existing world state");
    }

    @ExceptionHandler(GraphStoreUnavailableException.class)
    ResponseEntity<Map<String, Object>> graphUnavailable(Exception exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Graph Store Unavailable", exception.getMessage());
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class
    })
    ResponseEntity<Map<String, Object>> badRequest(Exception exception) {
        String detail = exception instanceof IllegalArgumentException
                ? exception.getMessage()
                : "The ontology request is invalid";
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", detail);
    }

    private ResponseEntity<Map<String, Object>> problem(
            HttpStatus status, String title, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Map.of("title", title, "status", status.value(), "detail", detail));
    }
}
