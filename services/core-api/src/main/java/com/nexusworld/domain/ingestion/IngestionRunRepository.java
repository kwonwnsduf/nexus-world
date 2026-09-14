package com.nexusworld.domain.ingestion;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, UUID> {
  List<IngestionRun> findTop50ByOrderByStartedAtDesc();
}
