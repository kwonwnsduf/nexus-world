package com.nexusworld.application.ontology;

import java.util.List;

public record ActionValidationResult(
        String actionCode,
        String actorType,
        String targetType,
        boolean valid,
        List<String> errors) {}
