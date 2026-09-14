package com.nexusworld.ingestion;

import static org.assertj.core.api.Assertions.*;

import com.nexusworld.application.ingestion.Hashing;
import com.nexusworld.application.ingestion.IngestionException;
import com.nexusworld.infrastructure.ingestion.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class IngestionInfrastructureTest {
  @Test
  void redactsCredentialsFromPersistedUri() {
    String safe =
        UriTools.redact(
            URI.create(
                "https://example.test/data?apiKey=secret&country=KR&subscription-key=also-secret"));
    assertThat(safe)
        .contains("apiKey=REDACTED", "subscription-key=REDACTED", "country=KR")
        .doesNotContain("secret");
  }

  @Test
  void csvParserSupportsQuotedCommasAndEscapedQuotes() {
    var rows = CsvRows.parse("id,name\n1,\"Busan, Korea\"\n2,\"A \"\"quoted\"\" port\"\n");
    assertThat(rows.get(0).get("name")).isEqualTo("Busan, Korea");
    assertThat(rows.get(1).get("name")).isEqualTo("A \"quoted\" port");
  }

  @Test
  void zipSlipEntriesAreRejected() throws Exception {
    var bytes = new java.io.ByteArrayOutputStream();
    try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
      zip.putNextEntry(new java.util.zip.ZipEntry("../escape.csv"));
      zip.write("x".getBytes(StandardCharsets.UTF_8));
    }
    assertThatThrownBy(() -> Payloads.unzip(bytes.toByteArray()))
        .isInstanceOf(IngestionException.class);
  }

  @Test
  void hashesAreStable() {
    assertThat(Hashing.sha256("nexus")).isEqualTo(Hashing.sha256("nexus")).hasSize(64);
  }
}
