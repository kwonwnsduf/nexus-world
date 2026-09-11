package com.nexusworld.api.evidence;

import com.nexusworld.application.evidence.ProvenanceService;
import com.nexusworld.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1")
public class ProvenanceController {
    private final ProvenanceService service;
    public ProvenanceController(ProvenanceService service){this.service=service;}

    @PostMapping("/sources") @PreAuthorize("hasAnyRole('ANALYST','OPERATOR','ADMIN')")
    public ResponseEntity<SourceResponse> createSource(@Valid @RequestBody CreateSourceRequest request,@AuthenticationPrincipal CustomUserDetails user){
        SourceResponse response=SourceResponse.from(service.createSource(request.sourceKey(),request.sourceType(),request.title(),request.publisher(),
                request.canonicalUri(),request.sourceVersion(),request.license(),request.publishedAt(),request.retrievedAt(),request.contentSha256(),request.metadata(),user.getUserId()));
        return ResponseEntity.created(URI.create("/api/v1/sources/"+response.id())).body(response);
    }
    @GetMapping("/sources/{id}") public SourceResponse getSource(@PathVariable UUID id){return SourceResponse.from(service.getSource(id));}

    @PostMapping("/evidence") @PreAuthorize("hasAnyRole('ANALYST','OPERATOR','ADMIN')")
    public ResponseEntity<EvidenceResponse> createEvidence(@Valid @RequestBody CreateEvidenceRequest request,@AuthenticationPrincipal CustomUserDetails user){
        EvidenceResponse response=EvidenceResponse.from(service.createEvidence(request.sourceId(),request.evidenceType(),request.claimText(),request.locator(),
                request.excerpt(),request.measuredValue(),request.confidence(),request.observedAt(),request.validFrom(),request.validTo(),user.getUserId()));
        return ResponseEntity.created(URI.create("/api/v1/evidence/"+response.id())).body(response);
    }
    @GetMapping("/evidence/{id}") public EvidenceResponse getEvidence(@PathVariable UUID id){return EvidenceResponse.from(service.getEvidence(id));}

    @PostMapping("/assumptions") @PreAuthorize("hasAnyRole('ANALYST','OPERATOR','ADMIN')")
    public ResponseEntity<AssumptionResponse> createAssumption(@Valid @RequestBody CreateAssumptionRequest request,@AuthenticationPrincipal CustomUserDetails user){
        AssumptionResponse response=AssumptionResponse.from(service.createAssumption(request.assumptionKey(),request.category(),request.statement(),request.rationale(),
                request.assumedValue(),request.unit(),request.status(),request.confidence(),request.validFrom(),request.validTo(),user.getUserId()));
        return ResponseEntity.created(URI.create("/api/v1/assumptions/"+response.id())).body(response);
    }
    @GetMapping("/assumptions/{id}") public AssumptionResponse getAssumption(@PathVariable UUID id){return AssumptionResponse.from(service.getAssumption(id));}

    @PostMapping("/provenance-links") @PreAuthorize("hasAnyRole('ANALYST','OPERATOR','ADMIN')")
    public ResponseEntity<ProvenanceLinkResponse> createLink(@Valid @RequestBody CreateProvenanceLinkRequest request,@AuthenticationPrincipal CustomUserDetails user){
        ProvenanceLinkResponse response=ProvenanceLinkResponse.from(service.createLink(request.subjectType(),request.subjectId(),request.propertyPath(),request.evidenceId(),
                request.assumptionId(),request.transformation(),user.getUserId()));
        return ResponseEntity.created(URI.create("/api/v1/provenance-links/"+response.id())).body(response);
    }
    @GetMapping("/provenance-links")
    public List<ProvenanceLinkResponse> getLinks(@RequestParam @NotBlank String subjectType,@RequestParam UUID subjectId){
        return service.getLinks(subjectType.trim(),subjectId).stream().map(ProvenanceLinkResponse::from).toList();
    }
    @GetMapping("/provenance-links/{id}")
    public ProvenanceLinkResponse getLink(@PathVariable UUID id){return ProvenanceLinkResponse.from(service.getLink(id));}
}
