package com.nexusworld.api.evidence;

import com.nexusworld.application.evidence.DuplicateResourceException;
import com.nexusworld.application.evidence.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes=ProvenanceController.class)
public class ProvenanceExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class) ResponseEntity<Map<String,Object>> notFound(ResourceNotFoundException e){return problem(HttpStatus.NOT_FOUND,"Not Found",e.getMessage());}
    @ExceptionHandler({DuplicateResourceException.class,DataIntegrityViolationException.class}) ResponseEntity<Map<String,Object>> conflict(Exception e){return problem(HttpStatus.CONFLICT,"Conflict","The provenance resource conflicts with existing data");}
    @ExceptionHandler({MethodArgumentNotValidException.class,ConstraintViolationException.class,IllegalArgumentException.class}) ResponseEntity<Map<String,Object>> badRequest(Exception e){return problem(HttpStatus.BAD_REQUEST,"Bad Request","The provenance request is invalid");}
    private ResponseEntity<Map<String,Object>> problem(HttpStatus status,String title,String detail){return ResponseEntity.status(status).header("Content-Type","application/problem+json").body(Map.of("title",title,"status",status.value(),"detail",detail));}
}
