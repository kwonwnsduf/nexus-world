package com.nexusworld.domain.ontology;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OntologyPropertyTypeRepository
        extends JpaRepository<OntologyPropertyType, OntologyPropertyType.Key> {
    List<OntologyPropertyType> findByEntityTypeOrderByCodeAsc(String entityType);
}
