package com.nexusworld.api.ontology;
import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.ontology.OntologyEntityType;
import com.nexusworld.domain.ontology.OntologyActionType;
import com.nexusworld.domain.ontology.OntologyPropertyType;
import com.nexusworld.domain.ontology.OntologyRelationshipType;
import java.util.List;

public record OntologyResponse(
        String contractVersion,
        String ontologyVersion,
        List<EntityType> entityTypes,
        List<PropertyType> propertyTypes,
        List<RelationshipType> relationshipTypes,
        List<ActionType> actionTypes) {

    public record EntityType(
            String code, String domain, String displayName, String description,
            JsonNode requiredAttributes, boolean aggregateOnly) {

        static EntityType from(OntologyEntityType type) {
            return new EntityType(
                    type.getCode(), type.getDomain(), type.getDisplayName(), type.getDescription(),
                    type.getRequiredAttributes(), type.isAggregateOnly());
        }
    }

    public record PropertyType(
            String entityType,
            String code,
            String displayName,
            String description,
            String dataType,
            boolean required,
            String unit,
            String classification) {

        static PropertyType from(OntologyPropertyType type) {
            return new PropertyType(
                    type.getEntityType(), type.getCode(), type.getDisplayName(),
                    type.getDescription(), type.getDataType().name(), type.isRequired(),
                    type.getUnit(), type.getClassification());
        }
    }

    public record RelationshipType(
            String code, String sourceType, String targetType,
            String description, boolean temporal) {

        static RelationshipType from(OntologyRelationshipType type) {
            return new RelationshipType(
                    type.getCode(), type.getSourceType(), type.getTargetType(),
                    type.getDescription(), type.isTemporal());
        }
    }

    public record ActionType(
            String code,
            String actorType,
            String targetType,
            String description,
            JsonNode parametersSchema) {

        static ActionType from(OntologyActionType type) {
            return new ActionType(
                    type.getCode(), type.getActorType(), type.getTargetType(),
                    type.getDescription(), type.getParametersSchema());
        }
    }
}
