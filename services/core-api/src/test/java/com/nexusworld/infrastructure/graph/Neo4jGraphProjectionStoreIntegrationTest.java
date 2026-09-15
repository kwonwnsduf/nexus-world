package com.nexusworld.infrastructure.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusworld.config.GraphProperties;
import com.nexusworld.domain.ontology.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class Neo4jGraphProjectionStoreIntegrationTest {
  @Container static final Neo4jContainer<?> NEO4J =
      new Neo4jContainer<>("neo4j:5.26-community").withoutAuthentication();

  @Test void replacesProjectionAndReturnsIndustrialToSocietyThreeHopPath() {
    UUID world=UUID.randomUUID(), actor=UUID.randomUUID();
    var json = new ObjectMapper();
    var facility=node(world, actor, "FACILITY", "facility:fab", "Semiconductor fab", json);
    var company=node(world, actor, "COMPANY", "company:chip", "Chip company", json);
    var cohort=node(world, actor, "DEMOGRAPHIC_COHORT", "cohort:workers", "Workers", json);
    var household=node(world, actor, "HOUSEHOLD_ARCHETYPE", "household:worker", "Worker households", json);
    List<WorldGraphRelationship> edges=List.of(
        edge(world,actor,"OPERATES",company,facility,json),
        edge(world,actor,"EMPLOYS",company,cohort,json),
        edge(world,actor,"MEMBER_PROFILE_OF",cohort,household,json));
    var store = new Neo4jGraphProjectionStore(new GraphProperties(true, URI.create(NEO4J.getBoltUrl()),
        "neo4j", "", "neo4j", 100), json);
    try {
      var counts=store.replaceWorld(world,List.of(facility,company,cohort,household),edges);
      assertThat(counts.entityCount()).isEqualTo(4);
      assertThat(counts.relationshipCount()).isEqualTo(3);
      var paths=store.paths(world,facility.getId(),3,100);
      assertThat(paths).anySatisfy(path -> {
        assertThat(path.nodes()).hasSize(4);
        assertThat(path.nodes().get(3).entityType()).isEqualTo("HOUSEHOLD_ARCHETYPE");
        assertThat(path.relationships()).extracting(r -> r.relationshipType())
            .containsExactly("OPERATES","EMPLOYS","MEMBER_PROFILE_OF");
      });
    } finally { store.close(); }
  }

  private WorldGraphEntity node(UUID world, UUID actor, String type, String key, String name, ObjectMapper json) {
    return new WorldGraphEntity(UUID.randomUUID(),world,type,key,name,json.createObjectNode().put("test",true),
        null,null,actor,Instant.now());
  }
  private WorldGraphRelationship edge(UUID world, UUID actor, String type, WorldGraphEntity source,
      WorldGraphEntity target,ObjectMapper json) {
    return new WorldGraphRelationship(UUID.randomUUID(),world,type,source.getId(),source.getEntityType(),
        target.getId(),target.getEntityType(),json.createObjectNode(),null,null,actor,Instant.now());
  }
}
