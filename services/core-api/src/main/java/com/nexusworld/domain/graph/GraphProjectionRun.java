package com.nexusworld.domain.graph;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "graph_projection_runs")
public class GraphProjectionRun {
  public enum Status { RUNNING, SUCCEEDED, FAILED }
  @Id private UUID id;
  @Column(name = "world_version_id", nullable = false) private UUID worldVersionId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
  @Column(name = "entity_count", nullable = false) private int entityCount;
  @Column(name = "relationship_count", nullable = false) private int relationshipCount;
  @Column(name = "started_at", nullable = false) private Instant startedAt;
  @Column(name = "completed_at") private Instant completedAt;
  @Column(name = "error_message") private String errorMessage;
  @Column(name = "initiated_by", nullable = false) private UUID initiatedBy;

  protected GraphProjectionRun() {}
  public GraphProjectionRun(UUID id, UUID worldVersionId, Instant startedAt, UUID initiatedBy) {
    this.id=id; this.worldVersionId=worldVersionId; this.startedAt=startedAt;
    this.initiatedBy=initiatedBy; this.status=Status.RUNNING;
  }
  public void succeed(int entities, int relationships, Instant at) {
    status=Status.SUCCEEDED; entityCount=entities; relationshipCount=relationships; completedAt=at;
  }
  public void fail(String message, Instant at) {
    status=Status.FAILED; errorMessage=message == null ? "Unknown projection failure" : message.substring(0, Math.min(2000, message.length())); completedAt=at;
  }
  public UUID getId(){return id;} public UUID getWorldVersionId(){return worldVersionId;}
  public Status getStatus(){return status;} public int getEntityCount(){return entityCount;}
  public int getRelationshipCount(){return relationshipCount;} public Instant getStartedAt(){return startedAt;}
  public Instant getCompletedAt(){return completedAt;} public String getErrorMessage(){return errorMessage;}
}
