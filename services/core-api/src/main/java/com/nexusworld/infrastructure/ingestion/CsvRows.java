package com.nexusworld.infrastructure.ingestion;

import java.util.*;

public final class CsvRows {
  private CsvRows() {}

  public static List<Map<String, String>> parse(String text) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else if (c == '"') quoted = false;
        else field.append(c);
      } else if (c == '"') quoted = true;
      else if (c == ',') {
        row.add(field.toString());
        field.setLength(0);
      } else if (c == '\n') {
        row.add(stripCr(field.toString()));
        field.setLength(0);
        if (!row.stream().allMatch(String::isBlank)) rows.add(row);
        row = new ArrayList<>();
      } else field.append(c);
    }
    if (!field.isEmpty() || !row.isEmpty()) {
      row.add(stripCr(field.toString()));
      rows.add(row);
    }
    if (rows.isEmpty()) return List.of();
    List<String> headers = rows.get(0).stream().map(String::trim).toList();
    List<Map<String, String>> out = new ArrayList<>();
    for (int r = 1; r < rows.size(); r++) {
      Map<String, String> item = new LinkedHashMap<>();
      for (int c = 0; c < headers.size(); c++)
        item.put(headers.get(c), c < rows.get(r).size() ? rows.get(r).get(c).trim() : "");
      out.add(item);
    }
    return out;
  }

  private static String stripCr(String s) {
    return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
  }
}
