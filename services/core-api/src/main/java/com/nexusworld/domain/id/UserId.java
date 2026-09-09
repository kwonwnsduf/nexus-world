package com.nexusworld.domain.id;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) implements DomainId {
    public UserId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId parse(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return externalValue();
    }
}
