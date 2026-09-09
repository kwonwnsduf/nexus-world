package com.nexusworld.domain.id;

import java.util.Objects;
import java.util.UUID;

public record WorldVersionId(UUID value) implements DomainId {
    public WorldVersionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static WorldVersionId generate() {
        return new WorldVersionId(UUID.randomUUID());
    }

    public static WorldVersionId parse(String value) {
        return new WorldVersionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return externalValue();
    }
}
