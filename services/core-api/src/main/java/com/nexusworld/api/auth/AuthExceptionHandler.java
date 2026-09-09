package com.nexusworld.api.auth;

import com.nexusworld.application.auth.InvalidCredentialsException;
import com.nexusworld.application.auth.InvalidRefreshTokenException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Map<String, Object>> invalidCredentials() {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid username or password");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<Map<String, Object>> invalidRefreshToken() {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid or expired token");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", "Username and password are required");
    }

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status, String title, String detail) {
        return ResponseEntity.status(status)
                .header("Content-Type", "application/problem+json")
                .body(Map.of("title", title, "status", status.value(), "detail", detail));
    }
}
