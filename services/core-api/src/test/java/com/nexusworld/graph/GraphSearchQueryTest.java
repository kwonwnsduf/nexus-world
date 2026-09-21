package com.nexusworld.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexusworld.application.graph.GraphSearchQuery;
import org.junit.jupiter.api.Test;

class GraphSearchQueryTest {
  @Test
  void normalizesUnicodeWhitespaceAndBuildsBoundedOrQuery() {
    GraphSearchQuery query = GraphSearchQuery.from("  ＴＳＭＣ   대만 팹 대만  영향  ");

    assertThat(query.normalized()).isEqualTo("tsmc 대만 팹 대만 영향");
    assertThat(query.webSearch()).isEqualTo("tsmc OR 대만 OR 팹 OR 영향");
  }
}
