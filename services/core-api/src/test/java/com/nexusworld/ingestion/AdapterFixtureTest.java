package com.nexusworld.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexusworld.application.ingestion.*;
import com.nexusworld.infrastructure.ingestion.adapter.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdapterFixtureTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final RecordValidator validator = new RecordValidator();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sec",
        "opendart",
        "comtrade",
        "world-bank",
        "usgs",
        "wpi",
        "hs",
        "isic",
        "wpp",
        "ilostat",
        "kosis",
        "oecd",
        "unlocode"
      })
  void officialShapeFixtureParsesToValidCanonicalRecord(String source) throws Exception {
    ExternalDataAdapter adapter = adapter(source);
    byte[] content =
        source.equals("unlocode") ? unlocodeZip() : fixture(source + extension(source));
    FetchedPage page =
        new FetchedPage(
            "https://fixture.invalid/" + source,
            1,
            source.endsWith("csv") ? "text/csv" : "application/json",
            null,
            "fixture-v1",
            Instant.parse("2026-01-01T00:00:00Z"),
            content,
            mapper.createObjectNode());
    ObjectNode parameters =
        mapper.createObjectNode().put("version", source.equals("hs") ? "HS2022" : "fixture-v1");
    List<ParsedRecord> records = adapter.parse(page, parameters);
    assertThat(records).hasSize(1);
    assertThat(validator.validate(records.get(0))).isEmpty();
    assertThat(records.get(0).naturalKey()).isNotBlank();
    assertThat(records.get(0).sourceRecord()).isNotNull();
  }

  @Test
  void secCompanyFactsPreserveTaxonomyUnitAndFilingIdentity() throws Exception {
    String body =
        "{\"cik\":320193,\"entityName\":\"APPLE"
            + " INC\",\"facts\":{\"us-gaap\":{\"Assets\":{\"label\":\"Assets\","
            + "\"description\":\"Total assets\",\"units\":{\"USD\":[{\"fy\":2024,"
            + "\"fp\":\"FY\",\"form\":\"10-K\",\"filed\":\"2024-11-01\","
            + "\"start\":\"2023-10-01\",\"end\":\"2024-09-28\","
            + "\"val\":364980000000,\"accn\":\"0000320193-24-000123\"}]}}}}}";
    FetchedPage page =
        new FetchedPage(
            "https://fixture.invalid/companyfacts",
            1,
            "application/json",
            null,
            "v1",
            Instant.now(),
            body.getBytes(StandardCharsets.UTF_8),
            mapper.createObjectNode());
    ParsedRecord record =
        new SecAdapter(mapper, null, null).parse(page, mapper.createObjectNode()).get(0);
    assertThat(record.recordType()).isEqualTo("SEC_XBRL_FACT");
    assertThat(record.classificationVersion()).isEqualTo("us-gaap");
    assertThat(record.currencyCode()).isEqualTo("USD");
    assertThat(validator.validate(record)).isEmpty();
  }

  private ExternalDataAdapter adapter(String source) {
    return switch (source) {
      case "sec" -> new SecAdapter(mapper, null, null);
      case "opendart" -> new OpenDartAdapter(mapper, null, null);
      case "comtrade" -> new UnComtradeAdapter(mapper, null, null);
      case "world-bank" -> new WorldBankAdapter(mapper, null, null);
      case "usgs" -> new UsgsAdapter(mapper, null, null);
      case "wpi" -> new WpiAdapter(mapper, null, null);
      case "hs" -> new HsAdapter(mapper, null, null);
      case "isic" -> new IsicAdapter(mapper, null, null);
      case "wpp" -> new UnWppAdapter(mapper, null, null);
      case "ilostat" -> new IlostatAdapter(mapper, null, null);
      case "kosis" -> new KosisAdapter(mapper, null, null);
      case "oecd" -> new OecdAdapter(mapper, null, null);
      case "unlocode" -> new UnlocodeAdapter(mapper, null, null);
      default -> throw new IllegalArgumentException(source);
    };
  }

  private String extension(String source) {
    return Set.of("wpi", "isic", "wpp", "ilostat", "oecd").contains(source) ? ".csv" : ".json";
  }

  private byte[] fixture(String name) throws IOException {
    try (InputStream in = getClass().getResourceAsStream("/fixtures/ingestion/" + name)) {
      if (in == null) throw new FileNotFoundException(name);
      return in.readAllBytes();
    }
  }

  private byte[] unlocodeZip() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      zip.putNextEntry(new ZipEntry("2025-1 UNLOCODE CodeListPart1.csv"));
      zip.write(
          ("Change,Country,Location,Name,NameWoDiacritics,Subdivision,Function,"
                  + "Status,Date,IATA,Coordinates,Remarks\n"
                  + ",KR,PUS,Busan,Busan,26,1-------,AI,2501,,3510N 12903E,\n")
              .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return bytes.toByteArray();
  }
}
