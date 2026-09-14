package com.nexusworld.application.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class Hashing {
  private Hashing() {}

  public static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String sha256(String data) {
    return sha256(data.getBytes(StandardCharsets.UTF_8));
  }
}
