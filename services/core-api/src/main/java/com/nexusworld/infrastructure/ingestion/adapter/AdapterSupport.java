package com.nexusworld.infrastructure.ingestion.adapter;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.nexusworld.application.ingestion.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

abstract class AdapterSupport implements ExternalDataAdapter {
  protected final ObjectMapper mapper;

  AdapterSupport(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  protected JsonNode json(FetchedPage page) {
    try {
      return mapper.readTree(
          com.nexusworld.infrastructure.ingestion.Payloads.decode(
              page.content(), page.contentEncoding()));
    } catch (Exception e) {
      throw new IngestionException("Invalid JSON from " + source(), e);
    }
  }

  protected ObjectNode object(Map<String, String> row) {
    ObjectNode n = mapper.createObjectNode();
    row.forEach(n::put);
    return n;
  }

  protected ObjectNode dimensions(Object... values) {
    ObjectNode n = mapper.createObjectNode();
    for (int i = 0; i + 1 < values.length; i += 2)
      if (values[i + 1] != null) n.put(String.valueOf(values[i]), String.valueOf(values[i + 1]));
    return n;
  }

  protected JsonNode value(JsonNode raw, String... names) {
    for (String name : names) {
      JsonNode n = raw.get(name);
      if (n != null && !n.isNull()) {
        if (n.isNumber() || n.isBoolean()) return n;
        String s = n.asText();
        try {
          return DecimalNode.valueOf(new BigDecimal(s.replace(",", "")));
        } catch (NumberFormatException ignored) {
          return TextNode.valueOf(s);
        }
      }
    }
    return NullNode.instance;
  }

  protected String text(JsonNode n, String... names) {
    for (String name : names) {
      JsonNode v = n.get(name);
      if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText().trim();
    }
    return null;
  }

  protected String text(Map<String, String> n, String... names) {
    for (String name : names) {
      String v = n.get(name);
      if (v != null && !v.isBlank()) return v.trim();
    }
    return null;
  }

  protected LocalDate date(String value) {
    if (value == null) return null;
    try {
      if (value.matches("\\d{4}")) return LocalDate.of(Integer.parseInt(value), 1, 1);
      if (value.matches("\\d{6}"))
        return LocalDate.of(
            Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(4)), 1);
      if (value.matches("\\d{8}"))
        return LocalDate.parse(value, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
      return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
    } catch (DateTimeParseException | NumberFormatException e) {
      return null;
    }
  }

  protected Instant instant(String value) {
    if (value == null) return null;
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      try {
        return Instant.ofEpochMilli(Long.parseLong(value));
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
  }

  protected String required(JsonNode parameters, String name) {
    String v = text(parameters, name);
    if (v == null) throw new IngestionException(source() + " requires parameter: " + name);
    return v;
  }

  protected String param(JsonNode p, String name, String fallback) {
    String v = text(p, name);
    return v == null ? fallback : v;
  }

  protected ParsedRecord record(
      String type,
      String key,
      String country,
      String scheme,
      String code,
      String version,
      String currency,
      String unit,
      LocalDate start,
      LocalDate end,
      Instant observed,
      String dataVersion,
      JsonNode dimensions,
      JsonNode value,
      JsonNode raw) {
    return new ParsedRecord(
        type,
        key,
        country,
        scheme,
        code,
        version,
        currency,
        unit,
        start,
        end,
        observed,
        dataVersion,
        dimensions,
        value,
        raw);
  }
}
