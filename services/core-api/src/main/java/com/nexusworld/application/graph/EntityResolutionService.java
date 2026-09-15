package com.nexusworld.application.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.application.ontology.OntologyConflictException;
import com.nexusworld.application.ontology.OntologyService;
import com.nexusworld.domain.graph.*;
import com.nexusworld.domain.ontology.*;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntityResolutionService {
  private static final Pattern SCHEME = Pattern.compile("^[A-Z][A-Z0-9_]{1,47}$");
  private static final Set<String> TRUSTED_EXACT_SCHEMES = Set.of(
      "NATURAL_KEY", "LEI", "CIK", "DART_CORP_CODE", "ISO_3166_1_ALPHA2",
      "ISO_3166_1_ALPHA3", "UN_LOCODE", "HS_CODE", "ISIC_CODE", "IMO",
      "PORT_CODE", "POLICY_CODE", "PROGRAM_CODE", "POPULATION_MODEL_ID",
      "CLASSIFICATION_CODE");

  private final EntityResolutionKeyRepository keys;
  private final WorldGraphEntityRepository entities;
  private final OntologyService ontology;
  private final Clock clock;

  public EntityResolutionService(EntityResolutionKeyRepository keys,
      WorldGraphEntityRepository entities, OntologyService ontology, Clock clock) {
    this.keys=keys; this.entities=entities; this.ontology=ontology; this.clock=clock;
  }

  @Transactional
  public EntityResolutionResult resolve(UUID worldVersionId, String entityType,
      String naturalKey, String displayName, JsonNode attributes, List<Identifier> identifiers,
      String sourceSystem, Instant validFrom, Instant validTo, UUID actor) {
    List<Identifier> all = new ArrayList<>(identifiers == null ? List.of() : identifiers);
    all.add(new Identifier("NATURAL_KEY", naturalKey));
    LinkedHashMap<String, Identifier> normalized = new LinkedHashMap<>();
    for (Identifier identifier : all) {
      String scheme = normalizeScheme(identifier.scheme());
      if (!TRUSTED_EXACT_SCHEMES.contains(scheme)) {
        throw new IllegalArgumentException("Identifier scheme is not trusted for automatic merge: " + scheme);
      }
      String value = normalizeValue(identifier.value());
      normalized.put(scheme + "\u0000" + value, new Identifier(scheme, value));
    }

    LinkedHashSet<UUID> matches = new LinkedHashSet<>();
    List<String> matchedSchemes = new ArrayList<>();
    for (Identifier identifier : normalized.values()) {
      keys.findByWorldVersionIdAndEntityTypeAndKeySchemeAndNormalizedValue(
              worldVersionId, entityType, identifier.scheme(), identifier.value())
          .ifPresent(key -> { matches.add(key.getEntityId()); matchedSchemes.add(identifier.scheme()); });
    }
    entities.findByWorldVersionIdAndNaturalKey(worldVersionId, naturalKey.trim())
        .ifPresent(entity -> { matches.add(entity.getId()); matchedSchemes.add("NATURAL_KEY"); });
    if (matches.size() > 1) {
      throw new OntologyConflictException(
          "Identifiers resolve to different entities; automatic merge refused");
    }

    WorldGraphEntity entity;
    String decision;
    if (matches.isEmpty()) {
      entity = ontology.createEntity(worldVersionId, entityType, naturalKey, displayName,
          attributes, validFrom, validTo, actor);
      decision = "CREATED";
    } else {
      entity = entities.findById(matches.iterator().next())
          .orElseThrow(() -> new IllegalStateException("Resolution key references a missing entity"));
      if (!entity.getEntityType().equals(entityType)) {
        throw new OntologyConflictException("Resolved entity type does not match the candidate type");
      }
      decision = "MATCHED";
    }

    for (Identifier identifier : normalized.values()) {
      var existing = keys.findByWorldVersionIdAndEntityTypeAndKeySchemeAndNormalizedValue(
          worldVersionId, entityType, identifier.scheme(), identifier.value());
      if (existing.isEmpty()) {
        keys.save(new EntityResolutionKey(UUID.randomUUID(), worldVersionId, entity.getId(),
            entityType, identifier.scheme(), identifier.value(), blankToNull(sourceSystem), 1,
            clock.instant()));
      } else if (!existing.get().getEntityId().equals(entity.getId())) {
        throw new OntologyConflictException("Identifier is already owned by another entity");
      }
    }
    return new EntityResolutionResult(decision, entity, List.copyOf(new LinkedHashSet<>(matchedSchemes)));
  }

  private String normalizeScheme(String value) {
    if (value == null) throw new IllegalArgumentException("Identifier scheme is required");
    String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    if (!SCHEME.matcher(normalized).matches()) throw new IllegalArgumentException("Invalid identifier scheme");
    return normalized;
  }
  private String normalizeValue(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Identifier value is required");
    return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  }
  private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
  public record Identifier(String scheme, String value) {}
}
