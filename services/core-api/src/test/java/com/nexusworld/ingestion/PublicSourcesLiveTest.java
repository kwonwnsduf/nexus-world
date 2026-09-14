package com.nexusworld.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_INGESTION_TESTS", matches = "true")
class PublicSourcesLiveTest {
  static Stream<String> urls() {
    return Stream.of(
        "https://api.worldbank.org/v2/country/KOR/indicator/SP.POP.TOTL?format=json&date=2023",
        "https://earthquake.usgs.gov/fdsnws/event/1/version",
        "https://sdmx.oecd.org/public/rest/dataflow/all/all/latest?detail=allstubs");
  }

  @ParameterizedTest
  @MethodSource("urls")
  void officialPublicEndpointResponds(String url) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "NEXUS-WORLD-live-contract-test/1.0")
            .GET()
            .build();
    HttpResponse<Void> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    assertThat(response.statusCode()).isBetween(200, 299);
  }
}
