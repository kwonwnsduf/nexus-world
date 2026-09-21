package com.nexusworld.application.graph;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Pattern;

public record GraphSearchQuery(String normalized, String webSearch) {
  private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}_]+",
      Pattern.UNICODE_CHARACTER_CLASS);

  public static GraphSearchQuery from(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    var matcher = TOKEN.matcher(normalized);
    LinkedHashSet<String> tokens = new LinkedHashSet<>();
    while (matcher.find() && tokens.size() < 16) {
      String token = matcher.group();
      if (token.codePoints().count() >= 2 || token.codePointAt(0) > 127) tokens.add(token);
    }
    String webSearch = String.join(" OR ", tokens);
    return new GraphSearchQuery(normalized, webSearch.isBlank() ? normalized : webSearch);
  }
}
