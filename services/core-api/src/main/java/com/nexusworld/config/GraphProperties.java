package com.nexusworld.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nexus.graph")
public record GraphProperties(
    boolean enabled, URI uri, String username, String password, String database, int queryLimit) {
  public GraphProperties {
    if (uri == null) uri = URI.create("bolt://localhost:7687");
    if (username == null || username.isBlank()) username = "neo4j";
    if (password == null) password = "";
    if (database == null || database.isBlank()) database = "neo4j";
    if (queryLimit < 1 || queryLimit > 1000) queryLimit = 100;
  }
}
