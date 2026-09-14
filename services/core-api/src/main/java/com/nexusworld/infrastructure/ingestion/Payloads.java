package com.nexusworld.infrastructure.ingestion;

import com.nexusworld.application.ingestion.IngestionException;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

public final class Payloads {
  private Payloads() {}

  public static byte[] decode(byte[] body, String encoding) {
    if ((encoding == null || !encoding.equalsIgnoreCase("gzip"))
        && !(body.length > 2 && (body[0] & 255) == 0x1f && (body[1] & 255) == 0x8b)) return body;
    try (var in = new GZIPInputStream(new ByteArrayInputStream(body))) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new IngestionException("Invalid gzip payload", e);
    }
  }

  public static Map<String, byte[]> unzip(byte[] body) {
    Map<String, byte[]> files = new LinkedHashMap<>();
    try (var in = new ZipInputStream(new ByteArrayInputStream(body))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          String name = entry.getName().replace('\\', '/');
          if (name.startsWith("/") || name.contains("../"))
            throw new IngestionException("Unsafe ZIP entry");
          files.put(name, in.readAllBytes());
        }
      }
      return files;
    } catch (IOException e) {
      throw new IngestionException("Invalid ZIP payload", e);
    }
  }

  public static String text(byte[] bytes) {
    if (bytes.length >= 3
        && (bytes[0] & 255) == 0xEF
        && (bytes[1] & 255) == 0xBB
        && (bytes[2] & 255) == 0xBF)
      return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    String utf8 = new String(bytes, StandardCharsets.UTF_8);
    if (utf8.indexOf('\uFFFD') < 0) return utf8;
    return new String(bytes, Charset.forName("windows-1252"));
  }
}
