package com.nexusworld.api.ontology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusworld.application.graph.*;
import com.nexusworld.application.ontology.OntologyService;
import com.nexusworld.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OntologyController {
    private final OntologyService service;
    private final EntityResolutionService resolution;
    private final GraphProjectionService projection;
    private final ObjectMapper json;

    public OntologyController(OntologyService service, EntityResolutionService resolution,
            GraphProjectionService projection, ObjectMapper json) {
        this.service = service;
        this.resolution = resolution;
        this.projection = projection;
        this.json = json;
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

    @PostMapping("/world-versions/{versionId}/graph/entities/resolve")
    @PreAuthorize("hasAnyRole('ANALYST','OPERATOR','ADMIN')")
    public EntityResolutionResponse resolveEntity(@PathVariable UUID versionId,
            @Valid @RequestBody ResolveGraphEntityRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        var identifiers = request.identifiers() == null ? java.util.List.<EntityResolutionService.Identifier>of()
                : request.identifiers().stream().map(value ->
                    new EntityResolutionService.Identifier(value.scheme(), value.value())).toList();
        return EntityResolutionResponse.from(resolution.resolve(versionId, request.entityType(),
                request.naturalKey(), request.displayName(), request.attributes(), identifiers,
                request.sourceSystem(), request.validFrom(), request.validTo(), user.getUserId()));
    }

    @PostMapping("/world-versions/{versionId}/graph/projections")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<GraphProjectionResponse> project(@PathVariable UUID versionId,
            @AuthenticationPrincipal CustomUserDetails user) {
        var result = projection.project(versionId, user.getUserId());
        var status = result.getStatus() == com.nexusworld.domain.graph.GraphProjectionRun.Status.SUCCEEDED
                ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(GraphProjectionResponse.from(result));
    }

    @GetMapping("/world-versions/{versionId}/graph/paths/{rootEntityId}")
    public GraphPathsResponse paths(@PathVariable UUID versionId, @PathVariable UUID rootEntityId,
            @RequestParam(defaultValue = "3") int maxDepth) {
        return GraphPathsResponse.from(versionId, rootEntityId, maxDepth,
                projection.paths(versionId, rootEntityId, maxDepth), json);
    }
}
