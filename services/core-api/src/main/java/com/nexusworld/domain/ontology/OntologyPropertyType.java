package com.nexusworld.domain.ontology;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "ontology_property_types")
@IdClass(OntologyPropertyType.Key.class)
public class OntologyPropertyType {
    @Id
    @Column(name = "entity_type", length = 48)
    private String entityType;

    @Id
    @Column(length = 80)
    private String code;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 16)
    private PropertyDataType dataType;

    @Column(nullable = false)
    private boolean required;

    @Column(length = 80)
    private String unit;

    @Column(length = 120)
    private String classification;

    protected OntologyPropertyType() {}

    public OntologyPropertyType(
            String entityType,
            String code,
            String displayName,
            String description,
            PropertyDataType dataType,
            boolean required,
            String unit,
            String classification) {
        this.entityType = entityType;
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.dataType = dataType;
        this.required = required;
        this.unit = unit;
        this.classification = classification;
    }

    public String getEntityType() { return entityType; }
    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public PropertyDataType getDataType() { return dataType; }
    public boolean isRequired() { return required; }
    public String getUnit() { return unit; }
    public String getClassification() { return classification; }

    public static class Key implements Serializable {
        private String entityType;
        private String code;

        public Key() {}

        public Key(String entityType, String code) {
            this.entityType = entityType;
            this.code = code;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(entityType, key.entityType)
                    && Objects.equals(code, key.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityType, code);
        }
    }
}
