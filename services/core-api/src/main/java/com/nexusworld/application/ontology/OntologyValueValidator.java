package com.nexusworld.application.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexusworld.domain.ontology.OntologyPropertyType;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class OntologyValueValidator {
    private OntologyValueValidator() {}

    static boolean matchesPropertyType(OntologyPropertyType property, JsonNode value) {
        if (value == null || value.isNull()) {
            return !property.isRequired();
        }
        return switch (property.getDataType()) {
            case STRING -> value.isTextual();
            case INTEGER -> value.isIntegralNumber();
            case NUMBER -> value.isNumber();
            case BOOLEAN -> value.isBoolean();
            case DATE -> value.isTextual() && isDate(value.asText());
            case DATE_TIME -> value.isTextual() && isDateTime(value.asText());
            case UUID -> value.isTextual() && isUuid(value.asText());
            case URI -> value.isTextual() && isAbsoluteUri(value.asText());
            case OBJECT -> value.isObject();
            case ARRAY -> value.isArray();
        };
    }

    static List<String> validateSchema(JsonNode schema, JsonNode value) {
        List<String> errors = new ArrayList<>();
        validateSchema(schema, value, "parameters", errors);
        return List.copyOf(errors);
    }

    private static void validateSchema(
            JsonNode schema, JsonNode value, String path, List<String> errors) {
        String type = schema.path("type").asText();
        if (!matchesJsonSchemaType(type, value)) {
            errors.add(path + " must be of type " + type);
            return;
        }

        if (value.isObject()) {
            validateObject(schema, value, path, errors);
        }
        if (value.isNumber()) {
            validateNumber(schema, value, path, errors);
        }
        if (value.isTextual()) {
            validateString(schema, value.asText(), path, errors);
        }
        validateEnum(schema, value, path, errors);
    }

    private static void validateObject(
            JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (schema.has("minProperties") && value.size() < schema.get("minProperties").asInt()) {
            errors.add(path + " must contain at least " + schema.get("minProperties").asInt()
                    + " properties");
        }

        Set<String> declared = new HashSet<>();
        JsonNode properties = schema.path("properties");
        if (properties.isObject()) {
            properties.properties().forEach(property -> {
                declared.add(property.getKey());
                JsonNode actual = value.get(property.getKey());
                if (actual != null) {
                    validateSchema(
                            property.getValue(), actual, path + "." + property.getKey(), errors);
                }
            });
        }

        JsonNode required = schema.path("required");
        if (required.isArray()) {
            required.forEach(name -> {
                if (!value.hasNonNull(name.asText())) {
                    errors.add(path + "." + name.asText() + " is required");
                }
            });
        }

        if (schema.has("additionalProperties")
                && !schema.get("additionalProperties").asBoolean()) {
            value.propertyStream()
                    .map(java.util.Map.Entry::getKey)
                    .filter(name -> !declared.contains(name))
                    .forEach(name -> errors.add(path + "." + name + " is not allowed"));
        }
    }

    private static void validateNumber(
            JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (schema.has("minimum")
                && value.decimalValue().compareTo(schema.get("minimum").decimalValue()) < 0) {
            errors.add(path + " must be greater than or equal to " + schema.get("minimum"));
        }
        if (schema.has("maximum")
                && value.decimalValue().compareTo(schema.get("maximum").decimalValue()) > 0) {
            errors.add(path + " must be less than or equal to " + schema.get("maximum"));
        }
    }

    private static void validateString(
            JsonNode schema, String value, String path, List<String> errors) {
        String format = schema.path("format").asText();
        if ("date".equals(format) && !isDate(value)) {
            errors.add(path + " must be an ISO-8601 date");
        }
        if ("date-time".equals(format) && !isDateTime(value)) {
            errors.add(path + " must be an ISO-8601 date-time");
        }
        if ("uuid".equals(format) && !isUuid(value)) {
            errors.add(path + " must be a UUID");
        }
        if ("uri".equals(format) && !isAbsoluteUri(value)) {
            errors.add(path + " must be an absolute URI");
        }
    }

    private static void validateEnum(
            JsonNode schema, JsonNode value, String path, List<String> errors) {
        JsonNode allowed = schema.path("enum");
        if (allowed.isArray() && !contains(allowed, value)) {
            errors.add(path + " is not one of the allowed values");
        }
    }

    private static boolean contains(JsonNode values, JsonNode expected) {
        for (JsonNode value : values) {
            if (value.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesJsonSchemaType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value != null && value.isObject();
            case "array" -> value != null && value.isArray();
            case "string" -> value != null && value.isTextual();
            case "integer" -> value != null && value.isIntegralNumber();
            case "number" -> value != null && value.isNumber();
            case "boolean" -> value != null && value.isBoolean();
            case "null" -> value == null || value.isNull();
            default -> true;
        };
    }

    private static boolean isDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static boolean isDateTime(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static boolean isUuid(String value) {
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isAbsoluteUri(String value) {
        try {
            return URI.create(value).isAbsolute();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
