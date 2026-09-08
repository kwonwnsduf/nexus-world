package com.nexusworld.infrastructure.ai;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class AiServiceUnavailableException extends RuntimeException {
    public AiServiceUnavailableException(Throwable cause) {
        super("AI service contract is unavailable", cause);
    }
}

