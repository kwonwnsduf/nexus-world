package com.nexusworld.domain.ontology;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldGraphEntityRepository extends JpaRepository<WorldGraphEntity, UUID> {
    List<WorldGraphEntity> findByWorldVersionIdOrderByEntityTypeAscNaturalKeyAsc(UUID worldVersionId);
    boolean existsByWorldVersionIdAndNaturalKey(UUID worldVersionId, String naturalKey);

    @Query(value = "SELECT count(*) > 0 FROM world_versions WHERE id = :id", nativeQuery = true)
    boolean worldVersionExists(@Param("id") UUID id);
}
