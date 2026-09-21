package com.nexusworld.infrastructure.graph;

import com.nexusworld.application.port.GraphEntitySearchStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresGraphEntitySearchStore implements GraphEntitySearchStore {
  private static final String SEARCH_SQL = """
      WITH alias_candidates AS (
          SELECT entity_id,
              max(CASE
                  WHEN normalized_alias = :query THEN 100.0
                  WHEN position(normalized_alias IN :query) > 0 THEN 80.0
                  ELSE strict_word_similarity(normalized_alias, :query) * 50.0
              END) AS score
          FROM graph_entity_aliases
          WHERE world_version_id = :worldVersionId
            AND (normalized_alias = :query OR normalized_alias <% :query)
          GROUP BY entity_id
      ), document_candidates AS (
          SELECT entity_id,
              ts_rank_cd(search_vector, websearch_to_tsquery('simple', :webSearchQuery)) * 20.0
                  AS score
          FROM graph_entity_search_documents
          WHERE world_version_id = :worldVersionId
            AND search_vector @@ websearch_to_tsquery('simple', :webSearchQuery)
      ), candidates AS (
          SELECT entity_id, score FROM alias_candidates
          UNION ALL
          SELECT entity_id, score FROM document_candidates
      )
      SELECT entity_id
      FROM candidates
      GROUP BY entity_id
      ORDER BY max(score) DESC, entity_id
      LIMIT :limit
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public PostgresGraphEntitySearchStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<UUID> search(UUID worldVersionId, String normalizedQuery, String webSearchQuery,
      int limit) {
    return jdbc.query(SEARCH_SQL, Map.of(
        "worldVersionId", worldVersionId,
        "query", normalizedQuery,
        "webSearchQuery", webSearchQuery,
        "limit", limit),
        (result, row) -> result.getObject("entity_id", UUID.class));
  }
}
