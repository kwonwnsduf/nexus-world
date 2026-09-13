package com.nexusworld.api.ontology;

import com.nexusworld.application.ontology.ActionValidationResult;
import java.util.List;

public record ActionValidationResponse(
        String contractVersion,
        String actionCode,
        String actorType,
        String targetType,
        boolean valid,
        List<String> errors) {

    static ActionValidationResponse from(ActionValidationResult result) {
        return new ActionValidationResponse(
                "v1", result.actionCode(), result.actorType(), result.targetType(),
                result.valid(), result.errors());
    }
}
