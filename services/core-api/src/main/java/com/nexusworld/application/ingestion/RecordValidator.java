package com.nexusworld.application.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RecordValidator {
  private static final Pattern ISO2 = Pattern.compile("^[A-Z]{2}$"),
      ISO3 = Pattern.compile("^[A-Z]{3}$"),
      M49 = Pattern.compile("^\\d{3}$");

  public List<String> validate(ParsedRecord r) {
    List<String> errors = new ArrayList<>();
    if (blank(r.recordType())) errors.add("recordType is required");
    if (blank(r.naturalKey())) errors.add("naturalKey is required");
    if (r.recordType() != null && r.recordType().length() > 80)
      errors.add("recordType exceeds 80 characters");
    if (r.naturalKey() != null && r.naturalKey().length() > 512)
      errors.add("naturalKey exceeds 512 characters");
    if (r.value() == null || r.value().isNull()) errors.add("value is required");
    if (r.dimensions() == null || !r.dimensions().isObject())
      errors.add("dimensions must be an object");
    if (r.periodEnd() != null
        && (r.periodStart() == null || r.periodEnd().isBefore(r.periodStart())))
      errors.add("invalid period");
    if (r.countryCode() != null && !validCountry(r.countryCode(), r.countryCodeScheme()))
      errors.add("country code does not match scheme");
    if (r.currencyCode() != null && !r.currencyCode().matches("^[A-Z]{3}$"))
      errors.add("currency must be ISO-4217 alpha-3");
    return errors;
  }

  private boolean validCountry(String code, String scheme) {
    if (scheme == null) return false;
    return switch (scheme) {
      case "ISO-3166-1-alpha-2" -> ISO2.matcher(code).matches();
      case "ISO-3166-1-alpha-3" -> ISO3.matcher(code).matches();
      case "UN-M49" -> M49.matcher(code).matches();
      default -> false;
    };
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
