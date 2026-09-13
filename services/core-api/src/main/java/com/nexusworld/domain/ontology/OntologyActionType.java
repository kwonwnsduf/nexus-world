package com.nexusworld.domain.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ontology_action_types")
public class OntologyActionType {
    @Id
    @Column(length = 64)
    private String code;

    @Column(name = "actor_type", nullable = false, length = 48)
    private String actorType;

    @Column(name = "target_type", nullable = false, length = 48)
    private String targetType;

    @Column(nullable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_schema", nullable = false, columnDefinition = "jsonb")
    private JsonNode parametersSchema;

    protected OntologyActionType() {}

    public OntologyActionType(
            String code,
            String actorType,
            String targetType,
            String description,
            JsonNode parametersSchema) {
        this.code = code;
        this.actorType = actorType;
        this.targetType = targetType;
        this.description = description;
        this.parametersSchema = parametersSchema;
    }

    public String getCode() { return code; }
    public String getActorType() { return actorType; }
    public String getTargetType() { return targetType; }
    public String getDescription() { return description; }
    public JsonNode getParametersSchema() { return parametersSchema; }
}
