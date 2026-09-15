package com.nexusworld.graph;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusworld.application.graph.EntityResolutionService;
import com.nexusworld.application.ontology.*;
import com.nexusworld.domain.graph.*;
import com.nexusworld.domain.ontology.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class EntityResolutionServiceTest {
  private final EntityResolutionKeyRepository keys = mock(EntityResolutionKeyRepository.class);
  private final WorldGraphEntityRepository entities = mock(WorldGraphEntityRepository.class);
  private final OntologyService ontology = mock(OntologyService.class);
  private final UUID world = UUID.randomUUID(), actor = UUID.randomUUID();
  private final ObjectMapper json = new ObjectMapper();
  private EntityResolutionService service;

  @BeforeEach void setUp() {
    service = new EntityResolutionService(keys, entities, ontology,
        Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC));
    when(keys.findByWorldVersionIdAndEntityTypeAndKeySchemeAndNormalizedValue(any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(keys.save(any())).thenAnswer(call -> call.getArgument(0));
  }

  @Test void createsOnceThenMatchesCaseNormalizedExactIdentifier() {
    WorldGraphEntity created = entity("company:samsung-electronics", "Samsung Electronics");
    when(ontology.createEntity(eq(world), eq("COMPANY"), any(), any(), any(), isNull(), isNull(), eq(actor)))
        .thenReturn(created);
    var first = service.resolve(world, "COMPANY", "company:samsung-electronics",
        "Samsung Electronics", json.createObjectNode(),
        List.of(new EntityResolutionService.Identifier("DART-CORP-CODE", " 00126380 ")),
        "OPENDART", null, null, actor);
    assertThat(first.decision()).isEqualTo("CREATED");
    verify(keys, times(2)).save(any(EntityResolutionKey.class));

    EntityResolutionKey key = new EntityResolutionKey(UUID.randomUUID(), world, created.getId(),
        "COMPANY", "DART_CORP_CODE", "00126380", "OPENDART", 1, Instant.now());
    when(keys.findByWorldVersionIdAndEntityTypeAndKeySchemeAndNormalizedValue(
        world, "COMPANY", "DART_CORP_CODE", "00126380")).thenReturn(Optional.of(key));
    when(entities.findById(created.getId())).thenReturn(Optional.of(created));
    var second = service.resolve(world, "COMPANY", "another-source-key", "SAMSUNG ELECTRONICS",
        json.createObjectNode(), List.of(new EntityResolutionService.Identifier("dart_corp_code", "00126380")),
        "SEC", null, null, actor);
    assertThat(second.decision()).isEqualTo("MATCHED");
    assertThat(second.entity().getId()).isEqualTo(created.getId());
  }

  @Test void refusesFuzzyNameSchemes() {
    assertThatThrownBy(() -> service.resolve(world, "COMPANY", "company:a", "Acme",
        json.createObjectNode(), List.of(new EntityResolutionService.Identifier("NAME", "Acme Inc")),
        null, null, null, actor))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not trusted");
    verifyNoInteractions(ontology);
  }

  @Test void refusesMergeWhenExactIdentifiersDisagree() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    when(keys.findByWorldVersionIdAndEntityTypeAndKeySchemeAndNormalizedValue(
        world, "COMPANY", "LEI", "lei-1")).thenReturn(Optional.of(
            new EntityResolutionKey(UUID.randomUUID(), world, first, "COMPANY", "LEI", "lei-1", null, 1, Instant.now())));
    when(keys.findByWorldVersionIdAndEntityTypeAndKeySchemeAndNormalizedValue(
        world, "COMPANY", "CIK", "cik-2")).thenReturn(Optional.of(
            new EntityResolutionKey(UUID.randomUUID(), world, second, "COMPANY", "CIK", "cik-2", null, 1, Instant.now())));
    assertThatThrownBy(() -> service.resolve(world, "COMPANY", "company:a", "Acme",
        json.createObjectNode(), List.of(new EntityResolutionService.Identifier("LEI", "lei-1"),
            new EntityResolutionService.Identifier("CIK", "cik-2")), null, null, null, actor))
        .isInstanceOf(OntologyConflictException.class).hasMessageContaining("automatic merge refused");
    verifyNoInteractions(ontology);
  }

  private WorldGraphEntity entity(String key, String name) {
    return new WorldGraphEntity(UUID.randomUUID(), world, "COMPANY", key, name,
        json.createObjectNode(), null, null, actor, Instant.now());
  }
}
