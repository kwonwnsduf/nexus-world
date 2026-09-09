package com.nexusworld.domain.id;

import java.util.Objects;
import java.util.UUID;

public record ScenarioBranchId(UUID value) implements DomainId {
    public ScenarioBranchId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ScenarioBranchId generate() {
        return new ScenarioBranchId(UUID.randomUUID());
    }

    public static ScenarioBranchId parse(String value) {
        return new ScenarioBranchId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return externalValue();
    }
}
