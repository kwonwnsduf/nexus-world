package com.nexusworld.application.port;

import java.util.UUID;

public interface RetrievalIndexer {
  void index(String documentId, String title, String content, String sourceUri,
      UUID dataSourceId, UUID evidenceId);
}
