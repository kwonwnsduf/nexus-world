package com.nexusworld.domain.id;

import java.util.UUID;

/** A strongly typed identifier for an aggregate or immutable domain record. */
public interface DomainId {
    UUID value();

    default String externalValue() {
        return value().toString();
    }
}
