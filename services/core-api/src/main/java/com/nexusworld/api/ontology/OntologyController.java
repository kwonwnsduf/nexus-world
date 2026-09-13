package com.nexusworld.api.ontology;

import com.nexusworld.application.ontology.OntologyService;
import com.nexusworld.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OntologyController {
    private final OntologyService service;

    public OntologyController(OntologyService service) {
        this.service = service;
    }

    @GetMapping("/ontology")
    public OntologyResponse ontology() {
        return new OntologyResponse(
                "v1", "economic-civilization-v1",
                service.entityTypes().stream().map(OntologyResponse.EntityType::from).toList(),
                service.propertyTypes().stream().map(OntologyResponse.PropertyType::from).toList(),
                service.relationshipTypes().stream()
                        .map(OntologyResponse.RelationshipType::from)
                        .toList(),
                service.actionTypes().stream().map(OntologyResponse.ActionType::from).toList());
    }

    @PostMapping("/ontology/actions/validate")
    public ActionValidationResponse validateAction(
            @Valid @RequestBody ValidateActionRequest request) {
        return ActionValidationResponse.from(service.validateAction(
                request.actionCode(), request.actorType(),
                request.targetType(), request.parameters()));
    }

    @PostMapping("/world-versions/{versionId}/graph/entities")
    @PreAuthorize("hasAnyRole('ANALYST','OPERATOR','ADMIN')")
    public ResponseEntity<GraphEntityResponse> createEntity(
            @PathVariable UUID versionId,
            @Valid @RequestBody CreateGraphEntityRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        GraphEntityResponse response = GraphEntityResponse.from(service.createEntity(
                versionId, request.entityType(), request.naturalKey(), request.displayName(),
                request.attributes(), request.validFrom(), request.validTo(), user.getUserId()));
        URI location = URI.create(
                "/api/v1/world-versions/" + versionId + "/graph/entities/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/world-versions/{versionId}/graph/relationships")
    @PreAuthorize("hasAnyRole('ANALYST','OPERATOR','ADMIN')")
    public ResponseEntity<GraphRelationshipResponse> createRelationship(
            @PathVariable UUID versionId,
            @Valid @RequestBody CreateGraphRelationshipRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        GraphRelationshipResponse response = GraphRelationshipResponse.from(
                service.createRelationship(
                        versionId, request.relationshipType(), request.sourceEntityId(),
                        request.targetEntityId(), request.attributes(), request.validFrom(),
                        request.validTo(), user.getUserId()));
        URI location = URI.create(
                "/api/v1/world-versions/" + versionId + "/graph/relationships/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/world-versions/{versionId}/graph")
    public WorldGraphResponse graph(@PathVariable UUID versionId) {
        return new WorldGraphResponse(
                "v1", "economic-civilization-v1", versionId,
                service.graphEntities(versionId).stream().map(GraphEntityResponse::from).toList(),
                service.graphRelationships(versionId).stream()
                        .map(GraphRelationshipResponse::from)
                        .toList());
    }
}
