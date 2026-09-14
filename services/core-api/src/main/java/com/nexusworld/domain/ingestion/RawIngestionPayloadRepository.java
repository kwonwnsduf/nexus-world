package com.nexusworld.domain.ingestion;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawIngestionPayloadRepository extends JpaRepository<RawIngestionPayload, UUID> {
  boolean existsBySourceSystemAndRequestUriAndContentSha256(
      SourceSystem source, String uri, String sha);
}
