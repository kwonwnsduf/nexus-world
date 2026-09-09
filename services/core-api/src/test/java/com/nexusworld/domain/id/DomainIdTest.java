package com.nexusworld.domain.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainIdTest {
    @Test
    void preservesTypeWhileRoundTrippingExternalValue() {
        UUID value = UUID.randomUUID();

        WorldId worldId = WorldId.parse(value.toString());
        ScenarioId scenarioId = ScenarioId.parse(value.toString());

        assertThat(worldId.value()).isEqualTo(value);
        assertThat(worldId.toString()).isEqualTo(value.toString());
        assertThat((Object) worldId).isNotEqualTo(scenarioId);
    }

    @Test
    void rejectsNullAndMalformedValues() {
        assertThatThrownBy(() -> new WorldId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WorldId.parse("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
    }
}
