package com.nexusworld.ontology;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.nexusworld.domain.ontology.*;
import com.nexusworld.application.ontology.OntologyService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class OntologyServiceTest {
    private final OntologyEntityTypeRepository types=mock(OntologyEntityTypeRepository.class);
    private final OntologyRelationshipTypeRepository relationTypes=mock(OntologyRelationshipTypeRepository.class);
    private final OntologyPropertyTypeRepository propertyTypes =
            mock(OntologyPropertyTypeRepository.class);
    private final OntologyActionTypeRepository actionTypes =
            mock(OntologyActionTypeRepository.class);
    private final WorldGraphEntityRepository entities=mock(WorldGraphEntityRepository.class);
    private final WorldGraphRelationshipRepository relationships=mock(WorldGraphRelationshipRepository.class);
    private final ObjectMapper json=new ObjectMapper();
    private OntologyService service; private UUID version; private UUID actor;

    @BeforeEach
    void setUp() {
        service = new OntologyService(
                types, relationTypes, propertyTypes, actionTypes, entities, relationships,
                Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC));
        version = UUID.randomUUID();
        actor = UUID.randomUUID();
        when(entities.worldVersionExists(version)).thenReturn(true);
    }

    @Test void createsAggregateCohortWithRequiredCalibrationIdentity(){
        ArrayNode required=json.createArrayNode().add("populationModelId").add("baseYear").add("weight");
        when(types.findById("DEMOGRAPHIC_COHORT")).thenReturn(Optional.of(
                new OntologyEntityType(
                        "DEMOGRAPHIC_COHORT", "SOCIETY", "Cohort",
                        "Weighted cohort", required, true)));
        when(propertyTypes.findByEntityTypeOrderByCodeAsc("DEMOGRAPHIC_COHORT"))
                .thenReturn(List.of(
                        property("populationModelId", PropertyDataType.STRING, true),
                        property("baseYear", PropertyDataType.INTEGER, true),
                        property("weight", PropertyDataType.NUMBER, true)));
        when(entities.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        WorldGraphEntity result = service.createEntity(
                version, "DEMOGRAPHIC_COHORT", "kr:cohort:1", "Workers",
                json.createObjectNode()
                        .put("populationModelId", "kr-v1")
                        .put("baseYear", 2025)
                        .put("weight", 1000),
                null, null, actor);
        assertThat(result.getEntityType()).isEqualTo("DEMOGRAPHIC_COHORT");
        assertThat(result.getCreatedAt()).isEqualTo(Instant.parse("2026-09-12T00:00:00Z"));
    }

    @Test void rejectsMissingCalibrationIdentityAndPersonalIdentifiers(){
        when(types.findById("DEMOGRAPHIC_COHORT")).thenReturn(Optional.of(
                new OntologyEntityType(
                        "DEMOGRAPHIC_COHORT", "SOCIETY", "Cohort", "Weighted cohort",
                        json.createArrayNode().add("populationModelId"), true)));
        when(propertyTypes.findByEntityTypeOrderByCodeAsc("DEMOGRAPHIC_COHORT"))
                .thenReturn(List.of(property(
                        "populationModelId", PropertyDataType.STRING, true)));
        assertThatThrownBy(() -> service.createEntity(
                version, "DEMOGRAPHIC_COHORT", "cohort:1", "Unsafe",
                json.createObjectNode().put("email", "real@example.test"), null, null, actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Personal identifier");
        assertThatThrownBy(() -> service.createEntity(
                version, "DEMOGRAPHIC_COHORT", "cohort:2", "Incomplete",
                json.createObjectNode(), null, null, actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("populationModelId");
        verify(entities, never()).save(any());
    }

    @Test void rejectsRelationshipThatIsNotInTypedGrammar(){
        UUID companyId=UUID.randomUUID(),cohortId=UUID.randomUUID();
        var company = new WorldGraphEntity(
                companyId, version, "COMPANY", "company:1", "Company",
                json.createObjectNode(), null, null, actor, Instant.now());
        var cohort = new WorldGraphEntity(
                cohortId, version, "DEMOGRAPHIC_COHORT", "cohort:1", "Cohort",
                json.createObjectNode(), null, null, actor, Instant.now());
        when(entities.findById(companyId)).thenReturn(Optional.of(company));
        when(entities.findById(cohortId)).thenReturn(Optional.of(cohort));

        assertThatThrownBy(() -> service.createRelationship(
                version, "LENDS_TO", companyId, cohortId, null, null, null, actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void validatesPropertyDataTypes() {
        when(types.findById("DEMOGRAPHIC_COHORT")).thenReturn(Optional.of(
                new OntologyEntityType(
                        "DEMOGRAPHIC_COHORT", "SOCIETY", "Cohort", "Weighted cohort",
                        json.createArrayNode().add("baseYear"), true)));
        when(propertyTypes.findByEntityTypeOrderByCodeAsc("DEMOGRAPHIC_COHORT"))
                .thenReturn(List.of(property("baseYear", PropertyDataType.INTEGER, true)));

        assertThatThrownBy(() -> service.createEntity(
                version, "DEMOGRAPHIC_COHORT", "cohort:wrong-year", "Wrong year",
                json.createObjectNode().put("baseYear", "2025"), null, null, actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseYear must be of type INTEGER");
    }

    @Test
    void validatesActionTypeWithoutExecutingIt() throws Exception {
        JsonNode schema = json.readTree("""
                {
                  "type": "object",
                  "required": ["reductionRatio"],
                  "additionalProperties": false,
                  "properties": {
                    "reductionRatio": {"type": "number", "minimum": 0, "maximum": 1}
                  }
                }
                """);
        when(actionTypes.findById("REDUCE_PRODUCTION")).thenReturn(Optional.of(
                new OntologyActionType(
                        "REDUCE_PRODUCTION", "COMPANY", "FACILITY",
                        "Propose a production reduction", schema)));

        var valid = service.validateAction(
                "REDUCE_PRODUCTION", "COMPANY", "FACILITY",
                json.createObjectNode().put("reductionRatio", 0.25));
        var invalid = service.validateAction(
                "REDUCE_PRODUCTION", "BANK", "FACILITY",
                json.createObjectNode().put("reductionRatio", 1.5));

        assertThat(valid.valid()).isTrue();
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.errors()).hasSize(2);
        verifyNoInteractions(relationships);
    }

    private OntologyPropertyType property(
            String code, PropertyDataType dataType, boolean required) {
        return new OntologyPropertyType(
                "DEMOGRAPHIC_COHORT", code, code, "Test property",
                dataType, required, null, null);
    }
}
