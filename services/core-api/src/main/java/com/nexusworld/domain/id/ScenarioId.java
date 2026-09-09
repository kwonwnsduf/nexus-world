package com.nexusworld.domain.id;

import java.util.Objects;
import java.util.UUID;

public record ScenarioId(UUID value) implements DomainId {
    public ScenarioId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ScenarioId generate() {
        return new ScenarioId(UUID.randomUUID());
    }

    public static ScenarioId parse(String value) {
        return new ScenarioId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return externalValue();
    }
}
