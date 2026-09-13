package com.nexusworld.api.ontology;
import java.util.List;
import java.util.UUID;

public record WorldGraphResponse(
        String contractVersion,
        String ontologyVersion,
        UUID worldVersionId,
        List<GraphEntityResponse> entities,
        List<GraphRelationshipResponse> relationships) {}
