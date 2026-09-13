package com.nexusworld.domain.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;

@Entity
@Table(name = "ontology_entity_types")
public class OntologyEntityType {
    @Id
    @Column(length = 48)
    private String code;

    @Column(nullable = false, length = 24)
    private String domain;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private String description;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "required_attributes", nullable = false, columnDefinition = "jsonb")
    private JsonNode requiredAttributes;

    @Column(name = "aggregate_only", nullable = false)
    private boolean aggregateOnly;

    protected OntologyEntityType() {}

    public OntologyEntityType(
            String code,
            String domain,
            String displayName,
            String description,
            JsonNode requiredAttributes,
            boolean aggregateOnly) {
        this.code = code;
        this.domain = domain;
        this.displayName = displayName;
        this.description = description;
        this.requiredAttributes = requiredAttributes;
        this.aggregateOnly = aggregateOnly;
    }

    public String getCode() { return code; }
    public String getDomain() { return domain; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public JsonNode getRequiredAttributes() { return requiredAttributes; }
    public boolean isAggregateOnly() { return aggregateOnly; }
}
