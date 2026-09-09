package com.nexusworld.domain.id;

import java.util.Objects;
import java.util.UUID;

public record WorldId(UUID value) implements DomainId {
    public WorldId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static WorldId generate() {
        return new WorldId(UUID.randomUUID());
    }

    public static WorldId parse(String value) {
        return new WorldId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return externalValue();
    }
}
