package com.nexusworld.api.ontology;
import com.nexusworld.domain.graph.GraphProjectionRun;
import java.time.Instant;
import java.util.UUID;
public record GraphProjectionResponse(String contractVersion, UUID projectionId, UUID worldVersionId,
    String status, int entityCount, int relationshipCount, Instant startedAt, Instant completedAt,
    String error) {
  static GraphProjectionResponse from(GraphProjectionRun run) {
    return new GraphProjectionResponse("v1", run.getId(), run.getWorldVersionId(), run.getStatus().name(),
        run.getEntityCount(), run.getRelationshipCount(), run.getStartedAt(), run.getCompletedAt(), run.getErrorMessage());
  }
}
