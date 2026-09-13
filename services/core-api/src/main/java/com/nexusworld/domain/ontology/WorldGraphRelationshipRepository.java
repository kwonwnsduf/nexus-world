package com.nexusworld.domain.ontology;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface WorldGraphRelationshipRepository extends JpaRepository<WorldGraphRelationship,UUID> {
    List<WorldGraphRelationship> findByWorldVersionIdOrderByRelationshipTypeAscCreatedAtAsc(UUID worldVersionId);
}
