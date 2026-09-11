package com.nexusworld.domain.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="provenance_links")
public class ProvenanceLink {
    @Id private UUID id;
    @Column(name="subject_type",nullable=false,length=80) private String subjectType;
    @Column(name="subject_id",nullable=false) private UUID subjectId;
    @Column(name="property_path",nullable=false,length=240) private String propertyPath;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="evidence_id") private EvidenceItem evidence;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="assumption_id") private Assumption assumption;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private JsonNode transformation;
    @Column(name="created_by",nullable=false) private UUID createdBy;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected ProvenanceLink() {}
    public ProvenanceLink(UUID id,String subjectType,UUID subjectId,String propertyPath,EvidenceItem evidence,Assumption assumption,
            JsonNode transformation,UUID createdBy,Instant createdAt){this.id=id;this.subjectType=subjectType;this.subjectId=subjectId;
        this.propertyPath=propertyPath;this.evidence=evidence;this.assumption=assumption;this.transformation=transformation;
        this.createdBy=createdBy;this.createdAt=createdAt;}
    public UUID getId(){return id;} public String getSubjectType(){return subjectType;} public UUID getSubjectId(){return subjectId;}
    public String getPropertyPath(){return propertyPath;} public EvidenceItem getEvidence(){return evidence;} public Assumption getAssumption(){return assumption;}
    public JsonNode getTransformation(){return transformation;} public UUID getCreatedBy(){return createdBy;} public Instant getCreatedAt(){return createdAt;}
}
