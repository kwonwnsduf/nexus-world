package com.nexusworld.application.world;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexusworld.application.graph.EntityResolutionService;
import com.nexusworld.application.graph.GraphProjectionService;
import com.nexusworld.application.evidence.ProvenanceService;
import com.nexusworld.application.ingestion.IngestionCompletionHandler;
import com.nexusworld.application.ingestion.Hashing;
import com.nexusworld.application.ontology.OntologyService;
import com.nexusworld.application.port.SimulationStore;
import com.nexusworld.application.port.RetrievalIndexer;
import com.nexusworld.config.GraphProperties;
import com.nexusworld.domain.ingestion.*;
import com.nexusworld.domain.evidence.*;
import com.nexusworld.domain.ontology.WorldGraphEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IngestionWorldBaselineBuilder implements IngestionCompletionHandler {
  private final NormalizedExternalRecordRepository records;
  private final SimulationStore worlds;
  private final EntityResolutionService resolution;
  private final OntologyService ontology;
  private final ObjectMapper json;
  private final GraphProperties graph;
  private final GraphProjectionService projection;
  private final Clock clock;
  private final ProvenanceService provenance;
  private final RetrievalIndexer retrieval;
  private final TransactionTemplate transactions;

  public IngestionWorldBaselineBuilder(NormalizedExternalRecordRepository records,
      SimulationStore worlds, EntityResolutionService resolution, OntologyService ontology,
      ObjectMapper json, GraphProperties graph, GraphProjectionService projection,
      ProvenanceService provenance, RetrievalIndexer retrieval, TransactionTemplate transactions,
      Clock clock) {
    this.records = records;
    this.worlds = worlds;
    this.resolution = resolution;
    this.ontology = ontology;
    this.json = json;
    this.graph = graph;
    this.projection = projection;
    this.clock = clock;
    this.provenance = provenance;
    this.retrieval = retrieval;
    this.transactions = transactions;
  }

  @Override
  public void onCompleted(UUID runId, SourceSystem source, int normalizedRecords, UUID actor) {
    List<NormalizedExternalRecord> loaded = records.findTop20000ByOrderByCreatedAtDesc();
    if (loaded.isEmpty()) return;
    Map<String, NormalizedExternalRecord> latest = new LinkedHashMap<>();
    loaded.forEach(record -> latest.putIfAbsent(
        record.getSourceSystem() + "\u0000" + record.getNaturalKey(), record));
    List<NormalizedExternalRecord> snapshot = List.copyOf(latest.values());
    List<UUID> snapshotIds = snapshot.stream().map(NormalizedExternalRecord::getId).toList();
    String snapshotFingerprint = Hashing.sha256(snapshot.stream()
        .map(value -> value.getId().toString()).sorted().reduce("", (left, right) -> left + "|" + right));
    if (worlds.hasWorldSnapshot("External data baseline", snapshotFingerprint)) return;

    // Provenance must commit before the AI service writes RAG chunks which reference it.
    // Calling the AI service from the same uncommitted transaction violates the database
    // provenance constraint and used to leave otherwise valid world versions as PARTIAL.
    List<IndexDocument> documents = transactions.execute(status -> reload(snapshotIds).stream()
        .map(record -> indexDocument(record, actor)).toList());
    if (documents == null) throw new IllegalStateException("Cannot prepare retrieval documents");
    int indexedDocuments = 0;
    List<String> failedDocuments = new ArrayList<>();
    for (IndexDocument document : documents) {
      try {
        index(document);
        indexedDocuments++;
      } catch (RuntimeException failure) {
        failedDocuments.add(document.recordId().toString());
      }
    }
    int indexed = indexedDocuments;
    List<String> failures = List.copyOf(failedDocuments);
    transactions.executeWithoutResult(status ->
        buildWorld(runId, actor, reload(snapshotIds), snapshotFingerprint, indexed, failures));
  }

  private List<NormalizedExternalRecord> reload(List<UUID> ids) {
    Map<UUID, NormalizedExternalRecord> loaded = new HashMap<>();
    records.findAllById(ids).forEach(record -> loaded.put(record.getId(), record));
    if (loaded.size() != ids.size()) {
      throw new IllegalStateException("Normalized snapshot changed while building the world");
    }
    return ids.stream().map(loaded::get).toList();
  }

  private void buildWorld(UUID runId, UUID actor, List<NormalizedExternalRecord> snapshot,
      String snapshotFingerprint, int indexedDocuments, List<String> failedDocuments) {
    boolean retrievalReady = indexedDocuments == snapshot.size();
    ObjectNode state = json.createObjectNode();
    state.put("baselineStatus", "INSUFFICIENT_DATA");
    state.putArray("companies");
    state.putArray("supplyLinks");
    ObjectNode manifest = state.putObject("manifest");
    manifest.put("asOfDate", clock.instant().atZone(ZoneOffset.UTC).toLocalDate().toString());
    manifest.put("createdAt", clock.instant().toString());
    manifest.put("ontologyVersion", "economic-civilization-v1");
    manifest.put("simulationRuleVersion", "industrial-v1");
    manifest.put("graphProjectionVersion", graph.enabled()
        ? "neo4j-v1" : "postgresql-authoritative-v1");
    manifest.put("triggerRunId", runId.toString());
    manifest.put("snapshotFingerprint", snapshotFingerprint);
    manifest.put("retrievalIndexStatus", retrievalReady ? "READY" : "PARTIAL");
    manifest.put("retrievalDocumentCount", indexedDocuments);
    manifest.put("expectedRetrievalDocumentCount", snapshot.size());
    ArrayNode retrievalFailures = manifest.putArray("retrievalFailedRecordIds");
    failedDocuments.forEach(retrievalFailures::add);
    manifest.putArray("assumptions");
    ArrayNode sources = manifest.putArray("sourceSnapshotIds");
    snapshot.stream().map(value -> value.getRun().getId()).distinct()
        .sorted().forEach(value -> sources.add(value.toString()));
    ArrayNode missing = state.putObject("coverage").putArray("missingForIndustrialSimulation");
    missing.add("company production capacity");
    missing.add("company inventory");
    missing.add("company demand");
    missing.add("company cost and workforce baseline");
    compileSimulationGraph(snapshot, state);

    var world = worlds.createNextWorldVersion("External data baseline", state, clock.instant());
    Map<String, WorldGraphEntity> countries = new LinkedHashMap<>();
    Map<String, WorldGraphEntity> companies = new LinkedHashMap<>();
    Map<String, WorldGraphEntity> indicators = new LinkedHashMap<>();
    Map<String, WorldGraphEntity> industries = new LinkedHashMap<>();
    Map<String, WorldGraphEntity> products = new LinkedHashMap<>();
    Map<String, WorldGraphEntity> transportModes = new LinkedHashMap<>();
    Map<String, WorldGraphEntity> ports = new LinkedHashMap<>();
    Map<WorldGraphEntity, NormalizedExternalRecord> crises = new LinkedHashMap<>();
    Set<String> mappedRelationships = new HashSet<>();
    Map<String, List<NormalizedExternalRecord>> bilateral = new LinkedHashMap<>();
    // Resolve DART profiles first so a cross-listed company receives one deterministic
    // natural key before SEC CIK observations attach through English-name aliases.
    snapshot.stream()
        .filter(record -> record.getSourceSystem() == SourceSystem.OPENDART)
        .filter(record -> "OPENDART_COMPANY_PROFILE".equals(record.getRecordType()))
        .forEach(record -> company(world.versionId(), record, actor, companies));
    for (NormalizedExternalRecord record : snapshot) {
      WorldGraphEntity company = company(world.versionId(), record, actor, companies);
      WorldGraphEntity reporter = country(world.versionId(), record.getCountryCode(),
          record.getCountryCodeScheme(), display(record, "reporterName"), record, actor, countries);
      String partnerCode = text(record.getDimensions(), "partnerCode");
      WorldGraphEntity partner = country(world.versionId(), partnerCode,
          "UN-M49", display(record, "partnerName"), record, actor, countries);
      if (record.getSourceSystem() == SourceSystem.UN_COMTRADE
          && reporter != null && partner != null && !reporter.getId().equals(partner.getId())) {
        bilateral.computeIfAbsent(reporter.getId() + ":" + partner.getId(), ignored -> new ArrayList<>())
            .add(record);
      }
      if (record.getSourceSystem() == SourceSystem.USGS && "EARTHQUAKE".equals(record.getRecordType())) {
        crises.put(crisis(world.versionId(), record, actor), record);
      }
      mapRecordGraph(world.versionId(), record, company, reporter, partner, actor, indicators,
          industries, products, transportModes, ports, mappedRelationships);
    }
    for (List<NormalizedExternalRecord> flows : bilateral.values()) {
      createTradeFlow(world.versionId(), flows, countries, actor);
    }
    mapCrisisPorts(world.versionId(), crises, ports.values(), actor, mappedRelationships);
    if (graph.enabled()) projection.project(world.versionId(), actor);
  }

  private WorldGraphEntity company(UUID versionId, NormalizedExternalRecord record, UUID actor,
      Map<String, WorldGraphEntity> cache) {
    String identifier;
    String scheme;
    String name;
    String jurisdiction;
    if (record.getSourceSystem() == SourceSystem.SEC) {
      identifier = text(record.getDimensions(), "cik");
      scheme = "CIK";
      name = Optional.ofNullable(text(record.getDimensions(), "entityName"))
          .orElse(text(record.getDimensions(), "companyName"));
      jurisdiction = "US";
    } else if (record.getSourceSystem() == SourceSystem.OPENDART) {
      identifier = text(record.getDimensions(), "corpCode");
      scheme = "DART_CORP_CODE";
      name = text(record.getDimensions(), "corpName");
      jurisdiction = "KR";
    } else {
      return null;
    }
    if (identifier == null || name == null) return null;
    String key = scheme + ":" + identifier;
    WorldGraphEntity existing = cache.get(key);
    if (existing != null) {
      link("WORLD_GRAPH_ENTITY", existing.getId(), "/observations", record, actor,
          "deterministic-company-identifier-mapper-v1");
      return existing;
    }
    ObjectNode attributes = evidence(record, actor);
    attributes.put("jurisdiction", jurisdiction);
    attributes.put("industryCode", "UNCLASSIFIED");
    attributes.put("industryCodeProvenance", "MISSING_IN_SOURCE");
    ArrayNode aliases = attributes.putArray("nameAliases");
    List<EntityResolutionService.NameAlias> nameAliases = new ArrayList<>();
    addNameAlias(aliases, nameAliases, name, null);
    if (record.getSourceSystem() == SourceSystem.OPENDART) {
      addNameAlias(aliases, nameAliases, text(record.getDimensions(), "corpName"), "ko");
      addNameAlias(aliases, nameAliases, text(record.getDimensions(), "corpNameEng"), "en");
    } else if (record.getSourceSystem() == SourceSystem.SEC) {
      addNameAlias(aliases, nameAliases, text(record.getDimensions(), "companyName"), "en");
      addNameAlias(aliases, nameAliases, text(record.getDimensions(), "entityName"), "en");
    }
    WorldGraphEntity entity = resolution.resolve(versionId, "COMPANY", "company:" + key,
        name, attributes, List.of(new EntityResolutionService.Identifier(scheme, identifier)), nameAliases,
        record.getSourceSystem().name(), start(record), end(record), actor).entity();
    link("WORLD_GRAPH_ENTITY", entity.getId(), "/jurisdiction", record, actor,
        "deterministic-company-identifier-mapper-v1");
    cache.put(key, entity);
    return entity;
  }

  private WorldGraphEntity country(UUID versionId, String code, String scheme, String name,
      NormalizedExternalRecord record, UUID actor, Map<String, WorldGraphEntity> cache) {
    if (code == null || code.isBlank() || scheme == null || scheme.isBlank()) return null;
    String key = scheme + ":" + code;
    WorldGraphEntity existing = cache.get(key);
    if (existing != null) {
      link("WORLD_GRAPH_ENTITY", existing.getId(), "/observations", record, actor,
          "deterministic-country-mapper-v1");
      return existing;
    }
    ObjectNode attributes = evidence(record, actor);
    attributes.put("countryCode", code);
    attributes.put("codeScheme", scheme);
    List<EntityResolutionService.Identifier> identifiers = new ArrayList<>();
    identifiers.add(new EntityResolutionService.Identifier(
        scheme.equalsIgnoreCase("UN-M49") ? "UN_M49" : normalizeScheme(scheme), code));
    String iso3 = Optional.ofNullable(tradeIso3(record, code))
        .orElseGet(() -> iso3FromAlpha2(code, scheme));
    if (iso3 != null) identifiers.add(
        new EntityResolutionService.Identifier("ISO_3166_1_ALPHA3", iso3));
    String naturalKey = iso3 == null ? "country:" + key
        : "country:ISO-3166-1-alpha-3:" + iso3;
    WorldGraphEntity entity = resolution.resolve(versionId, "COUNTRY", naturalKey,
        name == null ? code : name, attributes, identifiers, record.getSourceSystem().name(),
        start(record), end(record), actor).entity();
    link("WORLD_GRAPH_ENTITY", entity.getId(), "/countryCode", record, actor,
        "deterministic-country-mapper-v1");
    cache.put(key, entity);
    return entity;
  }

  private String tradeIso3(NormalizedExternalRecord record, String code) {
    if (record.getSourceSystem() != SourceSystem.UN_COMTRADE) return null;
    String field = code.equals(record.getCountryCode()) ? "reporterIso" : "partnerIso";
    String value = text(record.getDimensions(), field);
    return value != null && value.matches("[A-Z]{3}") ? value : null;
  }

  private String iso3FromAlpha2(String code, String scheme) {
    if (!normalizeScheme(scheme).equals("ISO_3166_1_ALPHA2") || !code.matches("[A-Za-z]{2}")) {
      return null;
    }
    try {
      return new Locale.Builder().setRegion(code.toUpperCase(Locale.ROOT)).build().getISO3Country();
    } catch (MissingResourceException | IllformedLocaleException exception) {
      return null;
    }
  }

  private void mapRecordGraph(UUID versionId, NormalizedExternalRecord record,
      WorldGraphEntity company, WorldGraphEntity country, WorldGraphEntity partner, UUID actor,
      Map<String, WorldGraphEntity> indicators, Map<String, WorldGraphEntity> industries,
      Map<String, WorldGraphEntity> products, Map<String, WorldGraphEntity> transportModes,
      Map<String, WorldGraphEntity> ports,
      Set<String> relationships) {
    if (country != null && isStatisticalObservation(record)) {
      WorldGraphEntity indicator = statisticalIndicator(versionId, record, actor, indicators);
      relationshipOnce(versionId, "HAS_INDICATOR", country, indicator, record, actor,
          relationships, true);
    }
    if (company != null && record.getSourceSystem() == SourceSystem.SEC
        && "SEC_XBRL_FACT".equals(record.getRecordType())) {
      WorldGraphEntity indicator = statisticalIndicator(versionId, record, actor, indicators);
      relationshipOnce(versionId, "HAS_INDICATOR", company, indicator, record, actor,
          relationships, true);
    }
    if (company != null) {
      String code = companyIndustryCode(record);
      if (code != null) {
        WorldGraphEntity industry = industry(versionId, record, code, actor, industries);
        relationshipOnce(versionId, "CLASSIFIED_AS", company, industry, record, actor,
            relationships, true);
      }
    }
    if (country != null && (record.getSourceSystem() == SourceSystem.UNLOCODE
        || record.getSourceSystem() == SourceSystem.WPI)) {
      WorldGraphEntity port = port(versionId, record, actor, ports);
      if (port != null) relationshipOnce(versionId, "LOCATED_IN", port, country, record, actor,
          relationships, true);
    }
    if (record.getSourceSystem() == SourceSystem.HS) {
      WorldGraphEntity product = product(versionId, record, record.getClassificationCode(), actor,
          products);
      String parentCode = text(record.getDimensions(), "parent");
      if (product != null && parentCode != null) {
        WorldGraphEntity parent = product(versionId, record, parentCode, actor, products);
        relationshipOnce(versionId, "PARENT_OF", parent, product, record, actor,
            relationships, false);
      }
    }
    if (record.getSourceSystem() == SourceSystem.UN_COMTRADE
        && country != null && partner != null && !country.getId().equals(partner.getId())) {
      String commodity = record.getClassificationCode();
      if (commodity != null && !commodity.equalsIgnoreCase("TOTAL")) {
        WorldGraphEntity product = product(versionId, record, commodity, actor, products);
        if (product != null) {
          boolean imports = "M".equalsIgnoreCase(text(record.getDimensions(), "flowCode"));
          WorldGraphEntity exporter = imports ? partner : country;
          WorldGraphEntity importer = imports ? country : partner;
          relationshipOnce(versionId, "PRODUCES", exporter, product, record, actor,
              relationships, true);
          relationshipOnce(versionId, "SUPPLIES", product, importer, record, actor,
              relationships, true);
          relationshipOnce(versionId, "DEPENDS_ON", importer, product, record, actor,
              relationships, true);
          String modeCode = text(record.getDimensions(), "motCode");
          if (modeCode != null && !modeCode.equals("0")) {
            WorldGraphEntity mode = transportMode(versionId, record, modeCode, actor,
                transportModes);
            relationshipOnce(versionId, "SHIPS_VIA", product, mode, record, actor,
                relationships, true);
          }
        }
      }
    }
    if (record.getSourceSystem() == SourceSystem.ISIC) {
      String code = record.getClassificationCode();
      if (code != null) industry(versionId, record, code, actor, industries);
    }
  }

  private boolean isStatisticalObservation(NormalizedExternalRecord record) {
    return record.getValue() != null && record.getValue().isNumber()
        && Set.of(SourceSystem.WORLD_BANK, SourceSystem.OECD, SourceSystem.KOSIS,
            SourceSystem.ILOSTAT, SourceSystem.UN_WPP).contains(record.getSourceSystem());
  }

  private WorldGraphEntity statisticalIndicator(UUID versionId,
      NormalizedExternalRecord record, UUID actor, Map<String, WorldGraphEntity> cache) {
    String key = record.getSourceSystem() + ":" + record.getNaturalKey();
    WorldGraphEntity existing = cache.get(key);
    if (existing != null) return existing;
    ObjectNode attributes = evidence(record, actor);
    attributes.put("metricCode", Optional.ofNullable(record.getClassificationCode())
        .orElse(record.getRecordType()));
    attributes.put("sourceSystem", record.getSourceSystem().name());
    attributes.put("unit", Optional.ofNullable(record.getUnitCode()).orElse("UNSPECIFIED"));
    if (record.getValue().isNumber()) attributes.set("value", record.getValue().deepCopy());
    if (record.getPeriodStart() != null) attributes.put("period", record.getPeriodStart().toString());
    WorldGraphEntity entity = ontology.createEntity(versionId, "STATISTICAL_INDICATOR",
        "indicator:" + Hashing.sha256(key),
        record.getSourceSystem() + " " + Optional.ofNullable(record.getClassificationCode())
            .orElse(record.getRecordType()),
        attributes, start(record), end(record), actor);
    link("WORLD_GRAPH_ENTITY", entity.getId(), "/value", record, actor,
        "source-statistical-indicator-v1");
    cache.put(key, entity);
    return entity;
  }

  private String companyIndustryCode(NormalizedExternalRecord record) {
    if (record.getSourceSystem() == SourceSystem.SEC) {
      String sic = text(record.getDimensions(), "sic");
      return sic == null ? null : "SIC:" + sic;
    }
    if (record.getSourceSystem() == SourceSystem.OPENDART) {
      String code = text(record.getDimensions(), "industryCode");
      return code == null ? null : "DART:" + code;
    }
    return null;
  }

  private WorldGraphEntity industry(UUID versionId, NormalizedExternalRecord record, String code,
      UUID actor, Map<String, WorldGraphEntity> cache) {
    String key = record.getSourceSystem() + ":" + code;
    WorldGraphEntity existing = cache.get(key);
    if (existing != null) return existing;
    ObjectNode attributes = evidence(record, actor);
    attributes.put("classificationCode", code);
    String description = Optional.ofNullable(text(record.getDimensions(), "sicDescription"))
        .orElse(text(record.getDimensions(), "description"));
    WorldGraphEntity entity = ontology.createEntity(versionId, "INDUSTRY",
        "industry:" + bounded(key), description == null ? code : description,
        attributes, start(record), end(record), actor);
    link("WORLD_GRAPH_ENTITY", entity.getId(), "/classificationCode", record, actor,
        "source-industry-classification-v1");
    cache.put(key, entity);
    return entity;
  }

  private WorldGraphEntity product(UUID versionId, NormalizedExternalRecord record, String code,
      UUID actor, Map<String, WorldGraphEntity> cache) {
    if (code == null || code.isBlank()) return null;
    String key = "HS:" + code;
    WorldGraphEntity existing = cache.get(key);
    if (existing != null) return existing;
    ObjectNode attributes = evidence(record, actor);
    attributes.put("unit", "classification-item");
    attributes.put("classificationCode", code);
    String description = text(record.getDimensions(), "description");
    WorldGraphEntity entity = ontology.createEntity(versionId, "PRODUCT",
        "product:" + bounded(key), description == null ? code : description,
        attributes, null, null, actor);
    link("WORLD_GRAPH_ENTITY", entity.getId(), "/classificationCode", record, actor,
        "source-product-classification-v1");
    cache.put(key, entity);
    return entity;
  }

  private WorldGraphEntity port(UUID versionId, NormalizedExternalRecord record, UUID actor,
      Map<String, WorldGraphEntity> cache) {
    if (record.getSourceSystem() == SourceSystem.UNLOCODE) {
      String function = text(record.getDimensions(), "function");
      if (function == null || !function.contains("1")) return null;
    }
    String code = record.getClassificationCode();
    if (code == null) return null;
    String key = record.getSourceSystem() + ":" + code;
    WorldGraphEntity existing = cache.get(key);
    if (existing != null) return existing;
    ObjectNode attributes = evidence(record, actor);
    attributes.put("unLocode", record.getSourceSystem() == SourceSystem.UNLOCODE
        ? code : "WPI:" + code);
    putNumber(attributes, "latitude", text(record.getDimensions(), "latitude"));
    putNumber(attributes, "longitude", text(record.getDimensions(), "longitude"));
    String name = text(record.getDimensions(), "name");
    WorldGraphEntity entity = ontology.createEntity(versionId, "PORT", "port:" + bounded(key),
        name == null ? code : name, attributes, start(record), end(record), actor);
    link("WORLD_GRAPH_ENTITY", entity.getId(), "/unLocode", record, actor,
        "source-port-location-v1");
    cache.put(key, entity);
    return entity;
  }

  private WorldGraphEntity transportMode(UUID versionId, NormalizedExternalRecord record,
      String code, UUID actor, Map<String, WorldGraphEntity> cache) {
    WorldGraphEntity existing = cache.get(code);
    if (existing != null) return existing;
    ObjectNode attributes = evidence(record, actor);
    attributes.put("modeCode", code);
    WorldGraphEntity entity = ontology.createEntity(versionId, "TRANSPORT_MODE",
        "transport-mode:" + bounded(code), "Transport mode " + code,
        attributes, start(record), end(record), actor);
    link("WORLD_GRAPH_ENTITY", entity.getId(), "/modeCode", record, actor,
        "source-transport-mode-v1");
    cache.put(code, entity);
    return entity;
  }

  private void mapCrisisPorts(UUID versionId,
      Map<WorldGraphEntity, NormalizedExternalRecord> crises,
      Collection<WorldGraphEntity> ports, UUID actor, Set<String> relationships) {
    for (Map.Entry<WorldGraphEntity, NormalizedExternalRecord> crisis : crises.entrySet()) {
      Double crisisLat = number(crisis.getKey().getAttributes(), "latitude");
      Double crisisLon = number(crisis.getKey().getAttributes(), "longitude");
      if (crisisLat == null || crisisLon == null) continue;
      for (WorldGraphEntity port : ports) {
        Double portLat = number(port.getAttributes(), "latitude");
        Double portLon = number(port.getAttributes(), "longitude");
        if (portLat == null || portLon == null) continue;
        double distanceKm = haversineKm(crisisLat, crisisLon, portLat, portLon);
        if (distanceKm > 500) continue;
        String key = "AFFECTS:" + crisis.getKey().getId() + ":" + port.getId();
        if (!relationships.add(key)) continue;
        ObjectNode attributes = evidence(crisis.getValue(), actor);
        attributes.put("mappingRule", "geospatial-proximity-v1");
        attributes.put("distanceKm", Math.round(distanceKm * 1000.0) / 1000.0);
        attributes.put("radiusKm", 500);
        attributes.put("provenanceClass", "MODEL_DERIVED");
        var relation = ontology.createRelationship(versionId, "AFFECTS", crisis.getKey().getId(),
            port.getId(), attributes, start(crisis.getValue()), end(crisis.getValue()), actor);
        link("WORLD_GRAPH_RELATIONSHIP", relation.getId(), "/distanceKm", crisis.getValue(), actor,
            "geospatial-proximity-v1");
      }
    }
  }

  private void putNumber(ObjectNode target, String field, String value) {
    if (value == null) return;
    try {
      double parsed = Double.parseDouble(value);
      if (Double.isFinite(parsed)) target.put(field, parsed);
    } catch (NumberFormatException ignored) {
      // Source-native non-decimal coordinates remain in the normalized record.
    }
  }

  private Double number(JsonNode value, String field) {
    JsonNode node = value.path(field);
    return node.isNumber() && Double.isFinite(node.asDouble()) ? node.asDouble() : null;
  }

  private double haversineKm(double firstLat, double firstLon, double secondLat, double secondLon) {
    double latDistance = Math.toRadians(secondLat - firstLat);
    double lonDistance = Math.toRadians(secondLon - firstLon);
    double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
        + Math.cos(Math.toRadians(firstLat)) * Math.cos(Math.toRadians(secondLat))
        * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
    return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private void relationshipOnce(UUID versionId, String type, WorldGraphEntity source,
      WorldGraphEntity target, NormalizedExternalRecord record, UUID actor,
      Set<String> relationships, boolean temporal) {
    String key = type + ":" + source.getId() + ":" + target.getId();
    if (!relationships.add(key)) return;
    ObjectNode attributes = evidence(record, actor);
    attributes.put("mappingRule", "normalized-source-record-v1");
    if (record.getValue() != null && record.getValue().isNumber()) {
      attributes.set("observedValue", record.getValue().deepCopy());
      attributes.put("unit", Optional.ofNullable(record.getUnitCode()).orElse("UNSPECIFIED"));
    }
    var relationship = ontology.createRelationship(versionId, type, source.getId(), target.getId(),
        attributes, temporal ? start(record) : null, temporal ? end(record) : null, actor);
    link("WORLD_GRAPH_RELATIONSHIP", relationship.getId(), "", record, actor,
        "normalized-source-relationship-v1");
  }

  private WorldGraphEntity crisis(UUID versionId, NormalizedExternalRecord record, UUID actor) {
    ObjectNode attributes = evidence(record, actor);
    attributes.put("eventKind", "EARTHQUAKE");
    attributes.put("startedAt", record.getObservedAt() == null
        ? record.getPeriodStart().atStartOfDay().toInstant(ZoneOffset.UTC).toString()
        : record.getObservedAt().toString());
    putNumber(attributes, "latitude", text(record.getDimensions(), "latitude"));
    putNumber(attributes, "longitude", text(record.getDimensions(), "longitude"));
    putNumber(attributes, "depthKm", text(record.getDimensions(), "depthKm"));
    if (record.getValue() != null && record.getValue().isNumber()) {
      attributes.set("magnitude", record.getValue().deepCopy());
    }
    WorldGraphEntity entity = resolution.resolve(versionId, "CRISIS_EVENT",
        "usgs:" + bounded(record.getNaturalKey()),
        Optional.ofNullable(text(record.getDimensions(), "place")).orElse(record.getNaturalKey()),
        attributes, List.of(), record.getSourceSystem().name(), start(record), end(record), actor)
        .entity();
    link("WORLD_GRAPH_ENTITY", entity.getId(), "/eventKind", record, actor,
        "deterministic-usgs-event-mapper-v1");
    return entity;
  }

  private void createTradeFlow(UUID versionId, List<NormalizedExternalRecord> flows,
      Map<String, WorldGraphEntity> countries, UUID actor) {
    NormalizedExternalRecord first = flows.get(0);
    boolean imports = "M".equalsIgnoreCase(text(first.getDimensions(), "flowCode"));
    WorldGraphEntity reporter = countries.get(first.getCountryCodeScheme() + ":" + first.getCountryCode());
    WorldGraphEntity partner = countries.get("UN-M49:" + text(first.getDimensions(), "partnerCode"));
    WorldGraphEntity source = imports ? partner : reporter;
    WorldGraphEntity target = imports ? reporter : partner;
    if (source == null || target == null) return;
    ObjectNode attributes = evidence(first, actor);
    ArrayNode observations = attributes.putArray("observations");
    for (NormalizedExternalRecord flow : flows) {
      ObjectNode observation = observations.addObject();
      observation.put("normalizedRecordId", flow.getId().toString());
      observation.put("commodityCode", flow.getClassificationCode());
      observation.put("currency", flow.getCurrencyCode());
      observation.set("value", flow.getValue().deepCopy());
      observation.put("provenanceClass", "OBSERVED");
    }
    var relationship = ontology.createRelationship(versionId, "TRADE_FLOW",
        source.getId(), target.getId(),
        attributes, start(first), end(first), actor);
    for (NormalizedExternalRecord flow : flows) {
      link("WORLD_GRAPH_RELATIONSHIP", relationship.getId(), "/observations", flow, actor,
          "deterministic-un-comtrade-mapper-v1");
    }
  }

  private ObjectNode evidence(NormalizedExternalRecord record, UUID actor) {
    EvidenceItem item = evidenceItem(record, actor);
    ObjectNode value = json.createObjectNode();
    value.put("normalizedRecordId", record.getId().toString());
    value.put("sourceUri", record.getProvenance().path("requestUri").asText());
    value.put("contentSha256", record.getProvenance().path("contentSha256").asText());
    value.put("sourceSystem", record.getSourceSystem().name());
    value.put("provenanceClass", "OBSERVED");
    value.put("evidenceId", item.getId().toString());
    value.put("dataSourceId", item.getSource().getId().toString());
    return value;
  }

  private EvidenceItem evidenceItem(NormalizedExternalRecord record, UUID actor) {
    String key = "normalized-record:" + record.getId();
    DataSource source = provenance.findSourceByKey(key).orElse(null);
    if (source == null) {
      ObjectNode metadata = json.createObjectNode();
      metadata.put("normalizedRecordId", record.getId().toString());
      metadata.put("rawPayloadId", record.getRawPayload().getId().toString());
      source = provenance.createSource(key, SourceType.API,
          record.getSourceSystem() + " observation " + bounded(record.getNaturalKey()),
          record.getSourceSystem().name(), record.getRawPayload().getRequestUri(),
          record.getRawPayload().getSourceVersion(), null, null,
          record.getRawPayload().getRetrievedAt(), record.getRawPayload().getContentSha256(),
          metadata, actor);
    } else {
      Optional<EvidenceItem> existing = provenance.findFirstEvidenceForSource(source.getId());
      if (existing.isPresent()) return existing.get();
    }
    ObjectNode locator = json.createObjectNode();
    locator.put("normalizedRecordId", record.getId().toString());
    return provenance.createEvidence(source.getId(), EvidenceType.MEASUREMENT,
        record.getRecordType() + " observed by " + record.getSourceSystem(), locator, null,
        record.getValue(), BigDecimal.ONE, record.getObservedAt(), start(record), end(record), actor);
  }

  private void compileSimulationGraph(List<NormalizedExternalRecord> snapshot, ObjectNode state) {
    List<NormalizedExternalRecord> trade = snapshot.stream()
        .filter(value -> value.getSourceSystem() == SourceSystem.UN_COMTRADE)
        .filter(value -> value.getValue() != null && value.getValue().isNumber())
        .filter(value -> value.getCountryCode() != null
            && text(value.getDimensions(), "partnerCode") != null)
        .toList();
    var latestPeriod = trade.stream().map(NormalizedExternalRecord::getPeriodStart)
        .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    if (latestPeriod != null) {
      trade = trade.stream().filter(value -> latestPeriod.equals(value.getPeriodStart())).toList();
    }

    Map<String, Double> supply = new LinkedHashMap<>();
    Map<String, Double> demand = new LinkedHashMap<>();
    Map<String, String> names = new LinkedHashMap<>();
    Map<String, Double> pairFlows = new LinkedHashMap<>();
    Map<String, List<String>> pairRecords = new LinkedHashMap<>();
    for (NormalizedExternalRecord value : trade) {
      String reporter = simulationCountryId(value, true);
      String partnerCode = text(value.getDimensions(), "partnerCode");
      String partner = simulationCountryId(value, false);
      if (reporter == null || partner == null) continue;
      boolean imports = "M".equalsIgnoreCase(text(value.getDimensions(), "flowCode"));
      String source = imports ? partner : reporter;
      String target = imports ? reporter : partner;
      double amount = value.getValue().asDouble();
      if (!Double.isFinite(amount) || amount < 0) continue;
      names.putIfAbsent(reporter, Optional.ofNullable(display(value, "reporterName"))
          .orElse(value.getCountryCode()));
      names.putIfAbsent(partner, Optional.ofNullable(display(value, "partnerName"))
          .orElse(partnerCode));
      supply.merge(source, amount, Double::sum);
      demand.merge(target, amount, Double::sum);
      String pair = source + "\u0000" + target;
      pairFlows.merge(pair, amount, Double::sum);
      pairRecords.computeIfAbsent(pair, ignored -> new ArrayList<>()).add(value.getId().toString());
    }

    Map<String, Map<String, MetricObservation>> observedMetrics = new LinkedHashMap<>();
    for (NormalizedExternalRecord record : snapshot) {
      String metric = simulationMetric(record);
      String entityId = simulationCountryId(record, true);
      if (metric == null || entityId == null || record.getValue() == null
          || !record.getValue().isNumber()) continue;
      double numeric = record.getValue().asDouble();
      if (!Double.isFinite(numeric) || numeric < 0) continue;
      MetricObservation candidate = new MetricObservation(numeric, record);
      observedMetrics.computeIfAbsent(entityId, ignored -> new LinkedHashMap<>())
          .merge(metric, candidate, this::newerMetric);
      names.putIfAbsent(entityId, Optional.ofNullable(statisticalAreaName(record))
          .orElse(record.getCountryCode()));
    }
    Map<String, String> dartCompaniesByName = dartCompaniesByName(snapshot);
    for (NormalizedExternalRecord record : snapshot) {
      if (record.getSourceSystem() != SourceSystem.SEC
          || !"SEC_XBRL_FACT".equals(record.getRecordType())
          || record.getValue() == null || !record.getValue().isNumber()) continue;
      double numeric = record.getValue().asDouble();
      if (!Double.isFinite(numeric) || numeric < 0) continue;
      String companyId = simulationCompanyId(record, dartCompaniesByName);
      String metric = secMetric(record);
      if (companyId == null || metric == null) continue;
      observedMetrics.computeIfAbsent(companyId, ignored -> new LinkedHashMap<>())
          .merge(metric, new MetricObservation(numeric, record), this::newerMetric);
      names.putIfAbsent(companyId, Optional.ofNullable(text(record.getDimensions(), "entityName"))
          .orElse(companyId));
    }
    if (pairFlows.isEmpty() && observedMetrics.isEmpty()) return;

    state.put("simulationModel", "relationship-graph-v1");
    state.withObject("/manifest").put("simulationRuleVersion", "multi-source-relationship-graph-v2");
    ArrayNode nodes = state.putArray("nodes");
    Set<String> ids = new TreeSet<>();
    ids.addAll(supply.keySet());
    ids.addAll(demand.keySet());
    ids.addAll(observedMetrics.keySet());
    for (String id : ids) {
      ObjectNode node = nodes.addObject();
      node.put("entityId", id);
      node.put("entityType", id.startsWith("company:") ? "COMPANY" : "COUNTRY");
      node.put("displayName", names.getOrDefault(id, id));
      ObjectNode metrics = node.putObject("metrics");
      ObjectNode provenanceNode = node.putObject("provenance");
      if (supply.containsKey(id)) {
        metrics.put("SUPPLY", supply.get(id));
        provenanceNode.put("SUPPLY", "DERIVED_FROM_OBSERVED_TRADE_FLOW_SUM");
      }
      if (demand.containsKey(id)) {
        metrics.put("DEMAND", demand.get(id));
        provenanceNode.put("DEMAND", "DERIVED_FROM_OBSERVED_TRADE_FLOW_SUM");
      }
      for (Map.Entry<String, MetricObservation> metric :
          observedMetrics.getOrDefault(id, Map.of()).entrySet()) {
        metrics.put(metric.getKey(), metric.getValue().value());
        NormalizedExternalRecord source = metric.getValue().record();
        ObjectNode origin = provenanceNode.putObject(metric.getKey());
        origin.put("provenanceClass", "OBSERVED");
        origin.put("sourceSystem", source.getSourceSystem().name());
        origin.put("normalizedRecordId", source.getId().toString());
        origin.put("classificationCode", Optional.ofNullable(source.getClassificationCode()).orElse(""));
        origin.put("unit", Optional.ofNullable(source.getUnitCode()).orElse(""));
        if (source.getPeriodStart() != null) origin.put("period", source.getPeriodStart().toString());
      }
    }
    ArrayNode relationships = state.putArray("relationships");
    int ordinal = 0;
    for (Map.Entry<String, Double> entry : pairFlows.entrySet()) {
      String[] endpoints = entry.getKey().split("\u0000", -1);
      double destinationTotal = demand.getOrDefault(endpoints[1], 0.0);
      if (destinationTotal <= 0) continue;
      ObjectNode edge = relationships.addObject();
      edge.put("relationshipId", "trade-flow:" + (++ordinal));
      edge.put("relationshipType", "TRADE_FLOW");
      edge.put("sourceEntityId", endpoints[0]);
      edge.put("targetEntityId", endpoints[1]);
      ObjectNode parameters = edge.putObject("parameters");
      parameters.put("baselineFlow", entry.getValue());
      parameters.put("dependencyRatio", entry.getValue() / destinationTotal);
      ObjectNode provenanceEdge = edge.putObject("provenance");
      provenanceEdge.put("baselineFlow", "DERIVED_FROM_OBSERVED_RECORDS");
      provenanceEdge.put("dependencyRatio", "DERIVED:baselineFlow/destinationTotal");
      ArrayNode recordIds = provenanceEdge.putArray("normalizedRecordIds");
      pairRecords.get(entry.getKey()).forEach(recordIds::add);
    }
    if (!relationships.isEmpty()) {
      state.put("baselineStatus", "READY");
      ObjectNode coverage = json.createObjectNode();
      coverage.putArray("missingForRelationshipSimulation");
      state.set("coverage", coverage);
    } else if (!nodes.isEmpty()) {
      state.put("baselineStatus", "READY");
      ObjectNode coverage = json.createObjectNode();
      coverage.putArray("missingForRelationshipSimulation")
          .add("No grounded propagation relationships are available; node shocks are local only");
      state.set("coverage", coverage);
    }
  }

  private String simulationCountryId(NormalizedExternalRecord record, boolean reporter) {
    if (record.getSourceSystem() == SourceSystem.UN_COMTRADE) {
      String iso3 = text(record.getDimensions(), reporter ? "reporterIso" : "partnerIso");
      if (iso3 != null && iso3.matches("[A-Z]{3}")) {
        return "country:ISO-3166-1-alpha-3:" + iso3;
      }
      String code = reporter ? record.getCountryCode() : text(record.getDimensions(), "partnerCode");
      return code == null ? null : "country:UN-M49:" + code;
    }
    if (record.getCountryCode() == null || record.getCountryCodeScheme() == null) return null;
    String iso3 = iso3FromAlpha2(record.getCountryCode(), record.getCountryCodeScheme());
    if (iso3 != null) return "country:ISO-3166-1-alpha-3:" + iso3;
    return "country:" + record.getCountryCodeScheme() + ":" + record.getCountryCode();
  }

  private String simulationMetric(NormalizedExternalRecord record) {
    String prefix = switch (record.getSourceSystem()) {
      case WORLD_BANK -> "WORLD_BANK";
      case OECD -> "OECD";
      case KOSIS -> "KOSIS";
      case ILOSTAT -> aggregateLaborRow(record) ? "ILOSTAT" : null;
      case UN_WPP -> aggregatePopulationRow(record) ? "UN_WPP" : null;
      default -> null;
    };
    if (prefix == null || record.getClassificationCode() == null) return null;
    String code = record.getClassificationCode().toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    return code.isBlank() ? null : prefix + "_" + code;
  }

  private Map<String, String> dartCompaniesByName(List<NormalizedExternalRecord> snapshot) {
    Map<String, String> result = new LinkedHashMap<>();
    for (NormalizedExternalRecord record : snapshot) {
      if (record.getSourceSystem() != SourceSystem.OPENDART) continue;
      String corpCode = text(record.getDimensions(), "corpCode");
      String english = normalizeCompanyName(text(record.getDimensions(), "corpNameEng"));
      if (corpCode != null && english != null) {
        result.putIfAbsent(english, "company:DART_CORP_CODE:" + corpCode);
      }
    }
    return result;
  }

  private String simulationCompanyId(NormalizedExternalRecord record,
      Map<String, String> dartCompaniesByName) {
    String cik = text(record.getDimensions(), "cik");
    if (cik == null) return null;
    String secName = normalizeCompanyName(Optional.ofNullable(
        text(record.getDimensions(), "entityName"))
        .orElse(text(record.getDimensions(), "companyName")));
    if (secName != null) {
      List<Map.Entry<String, String>> matches = dartCompaniesByName.entrySet().stream()
          .filter(entry -> entry.getKey().equals(secName)
              || companyNameSimilarity(entry.getKey(), secName) >= 0.96)
          .toList();
      if (matches.size() == 1) return matches.get(0).getValue();
    }
    return "company:CIK:" + cik;
  }

  private String secMetric(NormalizedExternalRecord record) {
    String taxonomy = Optional.ofNullable(record.getClassificationVersion()).orElse("XBRL");
    String concept = record.getClassificationCode();
    if (concept == null) return null;
    return ("SEC_" + taxonomy + "_" + concept).toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
  }

  private String normalizeCompanyName(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", "");
    normalized = normalized.replaceAll(
        "(incorporated|inc|corp|corporation|company|co|ltd|limited|llc|plc)+$", "");
    return normalized.isBlank() ? null : normalized;
  }

  private double companyNameSimilarity(String left, String right) {
    int[][] distances = new int[left.length() + 1][right.length() + 1];
    for (int i = 0; i <= left.length(); i++) distances[i][0] = i;
    for (int j = 0; j <= right.length(); j++) distances[0][j] = j;
    for (int i = 1; i <= left.length(); i++) for (int j = 1; j <= right.length(); j++) {
      distances[i][j] = Math.min(Math.min(distances[i - 1][j] + 1,
          distances[i][j - 1] + 1), distances[i - 1][j - 1]
          + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
    }
    return 1.0 - (double) distances[left.length()][right.length()]
        / Math.max(left.length(), right.length());
  }

  private boolean aggregateLaborRow(NormalizedExternalRecord record) {
    String sex = text(record.getDimensions(), "sex");
    String classif1 = text(record.getDimensions(), "classif1");
    String classif2 = text(record.getDimensions(), "classif2");
    return (sex == null || Set.of("SEX_T", "TOTAL", "T").contains(sex.toUpperCase(Locale.ROOT)))
        && classif1 == null && classif2 == null;
  }

  private boolean aggregatePopulationRow(NormalizedExternalRecord record) {
    String sex = text(record.getDimensions(), "sex");
    String age = text(record.getDimensions(), "age");
    return age == null && (sex == null || sex.equalsIgnoreCase("Both sexes")
        || sex.equalsIgnoreCase("Both") || sex.equalsIgnoreCase("Total"));
  }

  private String statisticalAreaName(NormalizedExternalRecord record) {
    return switch (record.getSourceSystem()) {
      case WORLD_BANK -> text(record.getDimensions(), "countryName");
      case UN_WPP -> text(record.getDimensions(), "location");
      default -> text(record.getDimensions(), "refArea");
    };
  }

  private MetricObservation newerMetric(MetricObservation current, MetricObservation candidate) {
    var currentPeriod = current.record().getPeriodStart();
    var candidatePeriod = candidate.record().getPeriodStart();
    if (currentPeriod == null) return candidatePeriod == null ? current : candidate;
    if (candidatePeriod == null || currentPeriod.isAfter(candidatePeriod)) return current;
    if (candidatePeriod.isAfter(currentPeriod)) return candidate;
    return current.record().getId().compareTo(candidate.record().getId()) <= 0 ? current : candidate;
  }

  private IndexDocument indexDocument(NormalizedExternalRecord record, UUID actor) {
    EvidenceItem item = evidenceItem(record, actor);
    String content = "source=" + record.getSourceSystem() + "\nrecordType="
        + record.getRecordType() + "\nnaturalKey=" + record.getNaturalKey()
        + "\ndimensions=" + record.getDimensions() + "\nvalue=" + record.getValue();
    return new IndexDocument(record.getId(), "normalized-record:" + record.getId(),
        record.getRecordType() + " " + record.getNaturalKey(), content,
        record.getRawPayload().getRequestUri(), item.getSource().getId(), item.getId());
  }

  private void index(IndexDocument document) {
    retrieval.index(document.documentId(), document.title(), document.content(),
        document.sourceUri(), document.dataSourceId(), document.evidenceId());
  }

  private void link(String subjectType, UUID subjectId, String path,
      NormalizedExternalRecord record, UUID actor, String ruleVersion) {
    EvidenceItem evidence = evidenceItem(record, actor);
    ObjectNode transformation = json.createObjectNode();
    transformation.put("provenanceClass", "OBSERVED");
    transformation.put("ruleVersion", ruleVersion);
    provenance.createLink(subjectType, subjectId, path, evidence.getId(), null,
        transformation, actor);
  }

  private Instant start(NormalizedExternalRecord value) {
    return value.getObservedAt() != null ? value.getObservedAt()
        : value.getPeriodStart() == null ? null
        : value.getPeriodStart().atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  private Instant end(NormalizedExternalRecord value) {
    return value.getPeriodEnd() == null ? null
        : value.getPeriodEnd().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1);
  }

  private String display(NormalizedExternalRecord record, String field) {
    String value = text(record.getDimensions(), field);
    return value == null || value.isBlank() ? null : value;
  }

  private void addNameAlias(ArrayNode target, List<EntityResolutionService.NameAlias> values,
      String value, String locale) {
    if (value == null || value.isBlank()
        || values.stream().anyMatch(existing -> value.trim().equalsIgnoreCase(existing.value()))) return;
    ObjectNode alias = target.addObject();
    alias.put("value", value.trim());
    if (locale != null) alias.put("locale", locale);
    values.add(new EntityResolutionService.NameAlias(value.trim(), locale));
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
  }

  private String normalizeScheme(String value) {
    String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_');
    if (normalized.contains("ALPHA_2")) return "ISO_3166_1_ALPHA2";
    if (normalized.contains("ALPHA_3")) return "ISO_3166_1_ALPHA3";
    return "CLASSIFICATION_CODE";
  }

  private String bounded(String value) {
    return value.substring(0, Math.min(180, value.length()));
  }

  private record IndexDocument(UUID recordId, String documentId, String title, String content,
      String sourceUri, UUID dataSourceId, UUID evidenceId) {}
  private record MetricObservation(double value, NormalizedExternalRecord record) {}
}
