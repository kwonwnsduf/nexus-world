package com.nexusworld.domain.id;

import java.util.Objects;
import java.util.UUID;

public record SimulationRunId(UUID value) implements DomainId {
    public SimulationRunId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SimulationRunId generate() {
        return new SimulationRunId(UUID.randomUUID());
    }

    public static SimulationRunId parse(String value) {
        return new SimulationRunId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return externalValue();
    }
}
