package com.nexusworld.infrastructure.ingestion;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class UriTools {
  private UriTools() {}

  public static URI build(URI base, String path, Map<String, String> query) {
    StringBuilder q = new StringBuilder();
    query.forEach(
        (k, v) -> {
          if (v != null && !v.isBlank()) {
            if (!q.isEmpty()) q.append('&');
            q.append(enc(k)).append('=').append(enc(v));
          }
        });
    return URI.create(base.toString().replaceAll("/$", "") + path + (q.isEmpty() ? "" : "?" + q));
  }

  public static String redact(URI uri) {
    String q = uri.getRawQuery();
    if (q == null) return uri.toString();
    String safe =
        Arrays.stream(q.split("&"))
            .map(
                p -> {
                  String k = p.split("=", 2)[0];
                  return k.toLowerCase(Locale.ROOT).matches(".*(key|token|secret).*")
                      ? k + "=REDACTED"
                      : p;
                })
            .reduce((a, b) -> a + "&" + b)
            .orElse("");
    try {
      return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), safe, null).toString();
    } catch (URISyntaxException e) {
      return uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
    }
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
