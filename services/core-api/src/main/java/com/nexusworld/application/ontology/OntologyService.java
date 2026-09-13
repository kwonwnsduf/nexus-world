package com.nexusworld.application.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nexusworld.domain.ontology.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OntologyService {
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "fullname",
            "email",
            "emailaddress",
            "phone",
            "phonenumber",
            "ssn",
            "socialsecuritynumber",
            "residentregistrationnumber",
            "nationalid",
            "passportnumber",
            "realpersonid");

    private final OntologyEntityTypeRepository entityTypes;
    private final OntologyRelationshipTypeRepository relationshipTypes;
    private final OntologyPropertyTypeRepository propertyTypes;
    private final OntologyActionTypeRepository actionTypes;
    private final WorldGraphEntityRepository entities;
    private final WorldGraphRelationshipRepository relationships;
    private final Clock clock;

    public OntologyService(
            OntologyEntityTypeRepository entityTypes,
            OntologyRelationshipTypeRepository relationshipTypes,
            OntologyPropertyTypeRepository propertyTypes,
            OntologyActionTypeRepository actionTypes,
            WorldGraphEntityRepository entities,
            WorldGraphRelationshipRepository relationships,
            Clock clock) {
        this.entityTypes = entityTypes;
        this.relationshipTypes = relationshipTypes;
        this.propertyTypes = propertyTypes;
        this.actionTypes = actionTypes;
        this.entities = entities;
        this.relationships = relationships;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OntologyEntityType> entityTypes() {
        return entityTypes.findAll().stream()
                .sorted(Comparator.comparing(OntologyEntityType::getCode))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OntologyRelationshipType> relationshipTypes() {
        return relationshipTypes.findAll().stream()
                .sorted(Comparator.comparing(OntologyRelationshipType::getCode)
                        .thenComparing(OntologyRelationshipType::getSourceType)
                        .thenComparing(OntologyRelationshipType::getTargetType))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OntologyPropertyType> propertyTypes() {
        return propertyTypes.findAll().stream()
                .sorted(Comparator.comparing(OntologyPropertyType::getEntityType)
                        .thenComparing(OntologyPropertyType::getCode))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OntologyActionType> actionTypes() {
        return actionTypes.findAll().stream()
                .sorted(Comparator.comparing(OntologyActionType::getCode))
                .toList();
    }

    @Transactional(readOnly = true)
    public ActionValidationResult validateAction(
            String actionCode,
            String actorType,
            String targetType,
            JsonNode parameters) {
        OntologyActionType definition = actionTypes.findById(actionCode)
                .orElseThrow(() -> new OntologyNotFoundException(
                        "Unknown ontology action type: " + actionCode));

        List<String> errors = new ArrayList<>();
        if (!definition.getActorType().equals(actorType)) {
            errors.add("actorType must be " + definition.getActorType());
        }
        if (!definition.getTargetType().equals(targetType)) {
            errors.add("targetType must be " + definition.getTargetType());
        }

        JsonNode value = objectOrEmpty(parameters);
        rejectPersonalIdentifiers(value, "parameters");
        errors.addAll(OntologyValueValidator.validateSchema(
                definition.getParametersSchema(), value));
        return new ActionValidationResult(
                actionCode, actorType, targetType, errors.isEmpty(), List.copyOf(errors));
    }

    @Transactional
    public WorldGraphEntity createEntity(
            UUID versionId,
            String typeCode,
            String naturalKey,
            String displayName,
            JsonNode attributes,
            Instant validFrom,
            Instant validTo,
            UUID actor) {
        requireWorld(versionId);
        OntologyEntityType type = entityTypes.findById(typeCode)
                .orElseThrow(() -> new OntologyNotFoundException(
                        "Unknown ontology entity type: " + typeCode));

        if (entities.existsByWorldVersionIdAndNaturalKey(versionId, naturalKey.trim())) {
            throw new OntologyConflictException("Natural key already exists in this world version");
        }

        JsonNode value = objectOrEmpty(attributes);
        requireObject(value);
        rejectPersonalIdentifiers(value, "");
        requireProperties(type.getCode(), value);
        requirePeriod(validFrom, validTo);

        return entities.save(new WorldGraphEntity(
                UUID.randomUUID(), versionId, typeCode, naturalKey.trim(), displayName.trim(), value,
                validFrom, validTo, actor, clock.instant()));
    }

    @Transactional
    public WorldGraphRelationship createRelationship(
            UUID versionId,
            String relationshipType,
            UUID sourceId,
            UUID targetId,
            JsonNode attributes,
            Instant validFrom,
            Instant validTo,
            UUID actor) {
        requireWorld(versionId);
        WorldGraphEntity source = entity(sourceId);
        WorldGraphEntity target = entity(targetId);

        if (!source.getWorldVersionId().equals(versionId)
                || !target.getWorldVersionId().equals(versionId)) {
            throw new IllegalArgumentException(
                    "Relationship endpoints must belong to the requested world version");
        }

        var key = new OntologyRelationshipType.Key(
                relationshipType, source.getEntityType(), target.getEntityType());
        OntologyRelationshipType definition = relationshipTypes.findById(key)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Relationship " + relationshipType + " is not allowed from "
                                + source.getEntityType() + " to " + target.getEntityType()));
        if (!definition.isTemporal() && (validFrom != null || validTo != null)) {
            throw new IllegalArgumentException(
                    "Non-temporal relationship cannot have a validity interval");
        }

        JsonNode value = objectOrEmpty(attributes);
        requireObject(value);
        rejectPersonalIdentifiers(value, "");
        requirePeriod(validFrom, validTo);

        return relationships.save(new WorldGraphRelationship(
                UUID.randomUUID(), versionId, relationshipType, sourceId, source.getEntityType(),
                targetId, target.getEntityType(), value, validFrom, validTo, actor, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<WorldGraphEntity> graphEntities(UUID versionId) {
        requireWorld(versionId);
        return entities.findByWorldVersionIdOrderByEntityTypeAscNaturalKeyAsc(versionId);
    }

    @Transactional(readOnly = true)
    public List<WorldGraphRelationship> graphRelationships(UUID versionId) {
        requireWorld(versionId);
        return relationships.findByWorldVersionIdOrderByRelationshipTypeAscCreatedAtAsc(versionId);
    }

    private void requireProperties(String entityType, JsonNode attributes) {
        List<OntologyPropertyType> definitions =
                propertyTypes.findByEntityTypeOrderByCodeAsc(entityType);
        if (definitions.isEmpty()) {
            throw new IllegalStateException(
                    "Ontology entity type has no property definitions: " + entityType);
        }

        for (OntologyPropertyType property : definitions) {
            JsonNode value = attributes.get(property.getCode());
            if (property.isRequired() && (value == null || value.isNull())) {
                throw new IllegalArgumentException(
                        "Missing required attribute: " + property.getCode());
            }
            if (value != null && !OntologyValueValidator.matchesPropertyType(property, value)) {
                throw new IllegalArgumentException(
                        "Attribute " + property.getCode() + " must be of type "
                                + property.getDataType());
            }
        }
    }

    private WorldGraphEntity entity(UUID id) {
        return entities.findById(id)
                .orElseThrow(() -> new OntologyNotFoundException("Graph entity not found"));
    }

    private void requireWorld(UUID id) {
        if (!entities.worldVersionExists(id)) {
            throw new OntologyNotFoundException("World version not found");
        }
    }

    private JsonNode objectOrEmpty(JsonNode value) {
        return value == null ? JsonNodeFactory.instance.objectNode() : value;
    }

    private void requireObject(JsonNode value) {
        if (!value.isObject()) {
            throw new IllegalArgumentException("attributes must be a JSON object");
        }
    }

    private void requirePeriod(Instant from, Instant to) {
        if (to != null && (from == null || to.isBefore(from))) {
            throw new IllegalArgumentException("validTo requires validFrom and must not precede it");
        }
    }

    private void rejectPersonalIdentifiers(JsonNode value, String path) {
        if (value.isObject()) {
            value.properties().forEach(field -> {
                String normalized = field.getKey()
                        .replaceAll("[^A-Za-z]", "")
                        .toLowerCase(Locale.ROOT);
                if (FORBIDDEN_KEYS.contains(normalized)) {
                    throw new IllegalArgumentException(
                            "Personal identifier is forbidden at " + path + "/" + field.getKey());
                }
                rejectPersonalIdentifiers(field.getValue(), path + "/" + field.getKey());
            });
        } else if (value.isArray()) {
            for (int index = 0; index < value.size(); index++) {
                rejectPersonalIdentifiers(value.get(index), path + "/" + index);
            }
        }
    }
}
