package com.nexusworld.application.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nexusworld.domain.ingestion.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IngestionService {
  private final Map<SourceSystem, ExternalDataAdapter> adapters;
  private final IngestionRunRepository runs;
  private final RawIngestionPayloadRepository payloads;
  private final NormalizedExternalRecordRepository records;
  private final IngestionRejectionRepository rejections;
  private final RecordValidator validator;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final TransactionTemplate transactions;
  private final List<IngestionCompletionHandler> completionHandlers;

  public IngestionService(
      List<ExternalDataAdapter> adapters,
      IngestionRunRepository runs,
      RawIngestionPayloadRepository payloads,
      NormalizedExternalRecordRepository records,
      IngestionRejectionRepository rejections,
      RecordValidator validator,
      ObjectMapper mapper,
      Clock clock,
      TransactionTemplate transactions,
      List<IngestionCompletionHandler> completionHandlers) {
    this.adapters =
        adapters.stream()
            .collect(
                Collectors.toUnmodifiableMap(ExternalDataAdapter::source, Function.identity()));
    this.runs = runs;
    this.payloads = payloads;
    this.records = records;
    this.rejections = rejections;
    this.validator = validator;
    this.mapper = mapper;
    this.clock = clock;
    this.transactions = transactions;
    this.completionHandlers = List.copyOf(completionHandlers);
  }

  public IngestionResult ingest(SourceSystem source, JsonNode parameters, UUID actor) {
    JsonNode params = parameters == null ? JsonNodeFactory.instance.objectNode() : parameters;
    if (!params.isObject()) throw new IllegalArgumentException("parameters must be a JSON object");
    params
        .fieldNames()
        .forEachRemaining(
            name -> {
              if (name.toLowerCase(Locale.ROOT).matches(".*(key|token|secret|password).*"))
                throw new IllegalArgumentException(
                    "Credentials must be supplied through environment variables, not request"
                        + " parameters");
            });
    if (canonical(params).length() > 32768)
      throw new IllegalArgumentException("parameters exceed 32 KiB");
    String requestKey = Hashing.sha256(canonical(params));
    IngestionRun run =
        new IngestionRun(
            UUID.randomUUID(), source, requestKey, params.deepCopy(), clock.instant(), actor);
    runs.save(run);
    int pages = 0, raw = 0, normalized = 0, rejected = 0;
    try {
      ExternalDataAdapter adapter =
          Optional.ofNullable(adapters.get(source))
              .orElseThrow(() -> new IngestionException("No adapter for " + source));
      List<FetchedPage> fetched = adapter.fetch(params);
      pages = fetched.size();
      for (FetchedPage page : fetched) {
        List<ParsedRecord> parsed = adapter.parse(page, params);
        raw += parsed.size();
        PersistCounts counts = transactions.execute(status -> persistPage(run, page, parsed));
        if (counts != null) {
          normalized += counts.normalized();
          rejected += counts.rejected();
        }
      }
      int fp = pages, fr = raw, fn = normalized, fj = rejected;
      transactions.executeWithoutResult(
          s -> {
            IngestionRun current = runs.findById(run.getId()).orElseThrow();
            current.succeed(clock.instant(), fp, fr, fn, fj);
          });
    } catch (RuntimeException e) {
      transactions.executeWithoutResult(
          s -> {
            IngestionRun current = runs.findById(run.getId()).orElse(run);
            current.fail(clock.instant(), rootMessage(e));
            runs.save(current);
          });
      throw e;
    }
    if (pages > 0) {
      for (IngestionCompletionHandler handler : completionHandlers) {
        handler.onCompleted(run.getId(), source, normalized, actor);
      }
    }
    return result(runs.findById(run.getId()).orElseThrow());
  }

  public IngestionResult get(UUID id) {
    return result(
        runs.findById(id).orElseThrow(() -> new IngestionException("Ingestion run not found")));
  }

  public List<IngestionResult> recent() {
    return runs.findTop50ByOrderByStartedAtDesc().stream().map(this::result).toList();
  }

  private PersistCounts persistPage(IngestionRun run, FetchedPage page, List<ParsedRecord> parsed) {
    String sha = Hashing.sha256(page.content());
    if (payloads.existsBySourceSystemAndRequestUriAndContentSha256(
        run.getSourceSystem(), page.requestUri(), sha)) return new PersistCounts(0, 0);
    RawIngestionPayload raw =
        payloads.save(
            new RawIngestionPayload(
                UUID.randomUUID(),
                run,
                run.getSourceSystem(),
                page.requestUri(),
                page.pageNumber(),
                page.mediaType(),
                page.contentEncoding(),
                page.sourceVersion(),
                page.retrievedAt(),
                sha,
                page.content(),
                page.responseMetadata()));
    int accepted = 0, rejected = 0;
    int index = 0;
    for (ParsedRecord record : parsed) {
      index++;
      List<String> errors = validator.validate(record);
      if (!errors.isEmpty()) {
        rejections.save(
            new IngestionRejection(
                UUID.randomUUID(),
                run,
                raw,
                "record[" + index + "]",
                "VALIDATION_FAILED",
                String.join("; ", errors),
                record.sourceRecord(),
                clock.instant()));
        rejected++;
        continue;
      }
      String fingerprint =
          Hashing.sha256(
              run.getSourceSystem()
                  + "|"
                  + record.naturalKey()
                  + "|"
                  + record.dataVersion()
                  + "|"
                  + record.periodStart()
                  + "|"
                  + canonical(record.dimensions())
                  + "|"
                  + canonical(record.value()));
      if (records.existsBySourceSystemAndFingerprint(run.getSourceSystem(), fingerprint)) continue;
      var provenance =
          mapper
              .createObjectNode()
              .put("rawPayloadId", raw.getId().toString())
              .put("requestUri", page.requestUri())
              .put("contentSha256", sha)
              .put("adapter", run.getSourceSystem().name())
              .put("transformationVersion", "day8-13-v1");
      records.save(
          new NormalizedExternalRecord(
              UUID.randomUUID(),
              run,
              raw,
              run.getSourceSystem(),
              record.recordType(),
              record.naturalKey(),
              fingerprint,
              record.countryCode(),
              record.countryCodeScheme(),
              record.classificationCode(),
              record.classificationVersion(),
              record.currencyCode(),
              record.unitCode(),
              record.periodStart(),
              record.periodEnd(),
              record.observedAt(),
              record.dataVersion(),
              record.dimensions(),
              record.value(),
              provenance,
              clock.instant()));
      accepted++;
    }
    return new PersistCounts(accepted, rejected);
  }

  private String canonical(JsonNode node) {
    try {
      return mapper
          .writer()
          .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new IngestionException("Cannot canonicalize ingestion data", e);
    }
  }

  private String rootMessage(Throwable e) {
    Throwable t = e;
    while (t.getCause() != null) t = t.getCause();
    return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
  }

  private IngestionResult result(IngestionRun r) {
    return new IngestionResult(
        r.getId(),
        r.getSourceSystem(),
        r.getStatus(),
        r.getStartedAt(),
        r.getCompletedAt(),
        r.getPagesFetched(),
        r.getRawRecords(),
        r.getNormalizedRecords(),
        r.getRejectedRecords(),
        r.getErrorMessage());
  }

  private record PersistCounts(int normalized, int rejected) {}
}
