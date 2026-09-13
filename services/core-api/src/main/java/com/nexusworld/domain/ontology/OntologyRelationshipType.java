package com.nexusworld.domain.ontology;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "ontology_relationship_types")
@IdClass(OntologyRelationshipType.Key.class)
public class OntologyRelationshipType {
    @Id
    @Column(length = 48)
    private String code;

    @Id
    @Column(name = "source_type", length = 48)
    private String sourceType;

    @Id
    @Column(name = "target_type", length = 48)
    private String targetType;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean temporal;

    protected OntologyRelationshipType() {}

    public String getCode() { return code; }
    public String getSourceType() { return sourceType; }
    public String getTargetType() { return targetType; }
    public String getDescription() { return description; }
    public boolean isTemporal() { return temporal; }

    public static class Key implements Serializable {
        private String code;
        private String sourceType;
        private String targetType;

        public Key() {}

        public Key(String code, String sourceType, String targetType) {
            this.code = code;
            this.sourceType = sourceType;
            this.targetType = targetType;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(code, key.code)
                    && Objects.equals(sourceType, key.sourceType)
                    && Objects.equals(targetType, key.targetType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, sourceType, targetType);
        }
    }
}
