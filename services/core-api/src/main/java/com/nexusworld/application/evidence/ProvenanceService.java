package com.nexusworld.application.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nexusworld.domain.evidence.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProvenanceService {
    private final DataSourceRepository sources; private final EvidenceItemRepository evidence;
    private final AssumptionRepository assumptions; private final ProvenanceLinkRepository links; private final Clock clock;
    public ProvenanceService(DataSourceRepository sources,EvidenceItemRepository evidence,AssumptionRepository assumptions,
            ProvenanceLinkRepository links,Clock clock){this.sources=sources;this.evidence=evidence;this.assumptions=assumptions;this.links=links;this.clock=clock;}

    @Transactional
    public DataSource createSource(String sourceKey,SourceType sourceType,String title,String publisher,String canonicalUri,
            String sourceVersion,String license,Instant publishedAt,Instant retrievedAt,String contentSha256,JsonNode metadata,UUID actor){
        if(sources.existsBySourceKey(sourceKey)) throw new DuplicateResourceException("Source key already exists");
        requireObject(metadata,"metadata");
        DataSource s=new DataSource(UUID.randomUUID(),sourceKey.trim(),sourceType,title.trim(),trim(publisher),
                trim(canonicalUri),trim(sourceVersion),trim(license),publishedAt,retrievedAt,contentSha256,
                objectOrEmpty(metadata),actor,clock.instant());
        return sources.save(s);
    }
    @Transactional(readOnly=true) public DataSource getSource(UUID id){return source(id);}
    @Transactional(readOnly=true) public DataSource getSourceByKey(String key){
        return sources.findBySourceKey(key).orElseThrow(()->new ResourceNotFoundException("Source not found"));
    }
    @Transactional(readOnly=true) public Optional<DataSource> findSourceByKey(String key){
        return sources.findBySourceKey(key);
    }
    @Transactional(readOnly=true) public EvidenceItem firstEvidenceForSource(UUID sourceId){
        return evidence.findFirstBySource_IdOrderByCreatedAtAsc(sourceId)
                .orElseThrow(()->new ResourceNotFoundException("Evidence not found"));
    }
    @Transactional(readOnly=true) public Optional<EvidenceItem> findFirstEvidenceForSource(UUID sourceId){
        return evidence.findFirstBySource_IdOrderByCreatedAtAsc(sourceId);
    }

    @Transactional
    public EvidenceItem createEvidence(UUID sourceId,EvidenceType evidenceType,String claimText,JsonNode locator,
            String excerpt,JsonNode measuredValue,BigDecimal confidence,Instant observedAt,Instant validFrom,Instant validTo,UUID actor){
        requireObject(locator,"locator");
        EvidenceItem item=new EvidenceItem(UUID.randomUUID(),source(sourceId),evidenceType,claimText.trim(),
                objectOrEmpty(locator),trim(excerpt),measuredValue,confidence,observedAt,validFrom,validTo,actor,clock.instant());
        return evidence.save(item);
    }
    @Transactional(readOnly=true) public EvidenceItem getEvidence(UUID id){return evidence(id);}

    @Transactional
    public Assumption createAssumption(String assumptionKey,String category,String statement,String rationale,JsonNode assumedValue,
            String unit,AssumptionStatus status,BigDecimal confidence,Instant validFrom,Instant validTo,UUID actor){
        if(assumptions.existsByAssumptionKey(assumptionKey)) throw new DuplicateResourceException("Assumption key already exists");
        Assumption a=new Assumption(UUID.randomUUID(),assumptionKey.trim(),category.trim(),statement.trim(),rationale.trim(),
                assumedValue,trim(unit),status,confidence,validFrom,validTo,actor,clock.instant());
        return assumptions.save(a);
    }
    @Transactional(readOnly=true) public Assumption getAssumption(UUID id){return assumption(id);}

    @Transactional
    public ProvenanceLink createLink(String subjectType,UUID subjectId,String propertyPath,UUID evidenceId,UUID assumptionId,
            JsonNode transformation,UUID actor){
        requireObject(transformation,"transformation");
        ProvenanceLink link=new ProvenanceLink(UUID.randomUUID(),subjectType.trim(),subjectId,propertyPath,
                evidenceId==null?null:evidence(evidenceId),assumptionId==null?null:assumption(assumptionId),
                objectOrEmpty(transformation),actor,clock.instant());
        return links.save(link);
    }
    @Transactional(readOnly=true)
    public List<ProvenanceLink> getLinks(String subjectType,UUID subjectId){
        return links.findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc(subjectType,subjectId);
    }
    @Transactional(readOnly=true) public ProvenanceLink getLink(UUID id){
        return links.findById(id).orElseThrow(()->new ResourceNotFoundException("Provenance link not found"));
    }
    private DataSource source(UUID id){return sources.findById(id).orElseThrow(()->new ResourceNotFoundException("Source not found"));}
    private EvidenceItem evidence(UUID id){return evidence.findById(id).orElseThrow(()->new ResourceNotFoundException("Evidence not found"));}
    private Assumption assumption(UUID id){return assumptions.findById(id).orElseThrow(()->new ResourceNotFoundException("Assumption not found"));}
    private JsonNode objectOrEmpty(JsonNode value){return value==null?JsonNodeFactory.instance.objectNode():value;}
    private void requireObject(JsonNode value,String name){if(value!=null&&!value.isObject())throw new IllegalArgumentException(name+" must be a JSON object");}
    private String trim(String value){return value==null?null:value.trim();}
}
