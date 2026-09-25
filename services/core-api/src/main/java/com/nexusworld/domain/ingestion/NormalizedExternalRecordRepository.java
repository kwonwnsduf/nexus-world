package com.nexusworld.domain.ingestion;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NormalizedExternalRecordRepository
    extends JpaRepository<NormalizedExternalRecord, UUID> {
  boolean existsBySourceSystemAndFingerprint(SourceSystem source, String fingerprint);
  List<NormalizedExternalRecord> findTop20000ByOrderByCreatedAtDesc();
  List<NormalizedExternalRecord> findByRun_IdOrderByCreatedAtAsc(UUID runId);
}
