package com.nexusworld.domain.ingestion;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRejectionRepository extends JpaRepository<IngestionRejection, UUID> {}
