package com.nexusworld.application.port;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public interface GraphRagClient {
  JsonNode query(UUID worldVersionId, String query, String authorization);
}
