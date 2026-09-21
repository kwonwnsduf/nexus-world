package com.nexusworld.application.port;

import java.util.List;
import java.util.UUID;

public interface GraphEntitySearchStore {
  List<UUID> search(UUID worldVersionId, String normalizedQuery, String webSearchQuery,
      int limit);
}
