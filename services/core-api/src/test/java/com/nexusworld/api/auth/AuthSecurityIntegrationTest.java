package com.nexusworld.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusworld.domain.auth.UserAccount;
import com.nexusworld.domain.auth.UserRepository;
import com.nexusworld.domain.auth.UserRole;
import com.nexusworld.domain.auth.UserStatus;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthSecurityIntegrationTest {
    private static final String VIEWER_PASSWORD = "viewer-password-123";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("nexusworld_auth")
                    .withUsername("nexusworld")
                    .withPassword("integration-only");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("nexus.auth.bootstrap.enabled", () -> "true");
        registry.add("nexus.auth.bootstrap.username", () -> "admin");
        registry.add("nexus.auth.bootstrap.password", () -> "nexus-world-local-admin");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createViewer() {
        if (!users.existsByNormalizedUsername("viewer")) {
            users.save(new UserAccount(
                    UUID.randomUUID(),
                    "viewer",
                    "viewer",
                    passwords.encode(VIEWER_PASSWORD),
                    UserStatus.ACTIVE,
                    Set.of(UserRole.VIEWER),
                    clock.instant()));
        }
    }

    @Test
    void loginIssuesTokenThatCanAccessCurrentUser() throws Exception {
        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"VIEWER","password":"viewer-password-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("v1"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(loginBody);
        String token = response.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("viewer"))
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"));
    }

    @Test
    void rejectsMissingTokenInvalidCredentialsAndInsufficientRole() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"viewer","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"unknown","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));

        String token = login("viewer", VIEWER_PASSWORD);
        mockMvc.perform(get("/api/v1/admin/access-check").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        String adminToken = login("admin", "nexus-world-local-admin");
        mockMvc.perform(get("/api/v1/admin/access-check").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void provenanceResourcesAreTraceableValidatedAndRoleProtected() throws Exception {
        String adminToken = login("admin", "nexus-world-local-admin");
        String suffix = UUID.randomUUID().toString();
        String sourceBody = mockMvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceKey":"test:%s",
                                  "sourceType":"DATASET",
                                  "title":"Integration dataset",
                                  "canonicalUri":"https://example.test/dataset",
                                  "retrievedAt":"2026-09-11T00:00:00Z",
                                  "metadata":{"country":"KR"}
                                }
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contractVersion").value("v1"))
                .andExpect(jsonPath("$.metadata.country").value("KR"))
                .andReturn().getResponse().getContentAsString();
        String sourceId = objectMapper.readTree(sourceBody).get("id").asText();

        String evidenceBody = mockMvc.perform(post("/api/v1/evidence")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceId":"%s",
                                  "evidenceType":"MEASUREMENT",
                                  "claimText":"Employment rate was 70 percent",
                                  "locator":{"table":"employment","row":"total"},
                                  "measuredValue":{"value":70,"unit":"percent"},
                                  "confidence":0.95
                                }
                                """.formatted(sourceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String evidenceId = objectMapper.readTree(evidenceBody).get("id").asText();

        String assumptionBody = mockMvc.perform(post("/api/v1/assumptions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assumptionKey":"labor.lag.%s",
                                  "category":"LABOR",
                                  "statement":"Employment reacts with a one-turn lag",
                                  "rationale":"No direct observation is available",
                                  "assumedValue":1,
                                  "unit":"turn",
                                  "status":"ACTIVE",
                                  "confidence":0.6
                                }
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String assumptionId = objectMapper.readTree(assumptionBody).get("id").asText();
        String subjectId = UUID.randomUUID().toString();

        String linkBody = mockMvc.perform(post("/api/v1/provenance-links")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectType":"WORLD_VERSION","subjectId":"%s","propertyPath":"/labor/employmentRate",
                                 "evidenceId":"%s","transformation":{"method":"weighted_mean"}}
                                """.formatted(subjectId, evidenceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String linkId = objectMapper.readTree(linkBody).get("id").asText();
        mockMvc.perform(post("/api/v1/provenance-links")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectType":"WORLD_VERSION","subjectId":"%s","propertyPath":"/labor/transitionLag",
                                 "assumptionId":"%s"}
                                """.formatted(subjectId, assumptionId)))
                .andExpect(status().isCreated());

        String viewerToken = login("viewer", VIEWER_PASSWORD);
        mockMvc.perform(get("/api/v1/provenance-links/" + linkId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value(evidenceId));
        mockMvc.perform(get("/api/v1/provenance-links")
                        .param("subjectType", "WORLD_VERSION").param("subjectId", subjectId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"sourceKey":"forbidden:%s","sourceType":"USER_INPUT","title":"Forbidden write",
                                 "retrievedAt":"2026-09-11T00:00:00Z"}
                                """.formatted(suffix)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/provenance-links")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectType":"WORLD_VERSION","subjectId":"%s","propertyPath":"/invalid",
                                 "evidenceId":"%s","assumptionId":"%s"}
                                """.formatted(subjectId, evidenceId, assumptionId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void economicCivilizationGraphEnforcesSemanticsPrivacyAndRoles() throws Exception {
        String adminToken=login("admin","nexus-world-local-admin");
        UUID worldId=UUID.randomUUID(),versionId=UUID.randomUUID();
        jdbc.update("INSERT INTO worlds (id,name) VALUES (?,?)",worldId,"Ontology integration world");
        jdbc.update("INSERT INTO world_versions (id,world_id,version_number) VALUES (?,?,?)",versionId,worldId,1);

        mockMvc.perform(get("/api/v1/ontology").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ontologyVersion").value("economic-civilization-v1"))
                .andExpect(jsonPath("$.entityTypes[?(@.code == 'COMPANY')]").exists())
                .andExpect(jsonPath("$.propertyTypes[?(@.entityType == 'COMPANY' && @.code == 'industryCode')]").exists())
                .andExpect(jsonPath("$.relationshipTypes[?(@.code == 'EMPLOYS')]").exists())
                .andExpect(jsonPath("$.actionTypes[?(@.code == 'REDUCE_PRODUCTION')]").exists());

        mockMvc.perform(post("/api/v1/ontology/actions/validate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionCode": "REDUCE_PRODUCTION",
                                  "actorType": "COMPANY",
                                  "targetType": "FACILITY",
                                  "parameters": {"reductionRatio": 0.25}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors.length()").value(0));

        mockMvc.perform(post("/api/v1/ontology/actions/validate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionCode": "REDUCE_PRODUCTION",
                                  "actorType": "BANK",
                                  "targetType": "FACILITY",
                                  "parameters": {"reductionRatio": 2}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors.length()").value(2));

        mockMvc.perform(post("/api/v1/world-versions/"+versionId+"/graph/entities")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entityType": "DEMOGRAPHIC_COHORT",
                                  "naturalKey": "kr:cohort:invalid-year",
                                  "displayName": "Invalid cohort",
                                  "attributes": {
                                    "populationModelId": "kr-2025-v1",
                                    "baseYear": "2025",
                                    "weight": 100
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());

        String companyBody=mockMvc.perform(post("/api/v1/world-versions/"+versionId+"/graph/entities")
                .header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content("""
                        {"entityType":"COMPANY","naturalKey":"kr:company:005930","displayName":"Semiconductor Company","attributes":{"jurisdiction":"KR","industryCode":"C26"}}
                        """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String cohortBody=mockMvc.perform(post("/api/v1/world-versions/"+versionId+"/graph/entities")
                .header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content("""
                        {"entityType":"DEMOGRAPHIC_COHORT","naturalKey":"kr:cohort:engineers","displayName":"Semiconductor engineers","attributes":{"populationModelId":"kr-2025-v1","baseYear":2025,"weight":12500}}
                        """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String companyId=objectMapper.readTree(companyBody).get("id").asText(),cohortId=objectMapper.readTree(cohortBody).get("id").asText();

        mockMvc.perform(post("/api/v1/world-versions/"+versionId+"/graph/relationships")
                .header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"relationshipType\":\"EMPLOYS\",\"sourceEntityId\":\""+companyId+"\",\"targetEntityId\":\""+cohortId+"\",\"attributes\":{\"jobs\":4200}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sourceEntityType").value("COMPANY"));
        mockMvc.perform(post("/api/v1/world-versions/"+versionId+"/graph/relationships")
                .header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"relationshipType\":\"LENDS_TO\",\"sourceEntityId\":\""+companyId+"\",\"targetEntityId\":\""+cohortId+"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/world-versions/"+versionId+"/graph/entities")
                .header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content("""
                        {"entityType":"DEMOGRAPHIC_COHORT","naturalKey":"unsafe","displayName":"Unsafe","attributes":{"populationModelId":"x","baseYear":2025,"weight":1,"email":"person@example.test"}}
                        """)).andExpect(status().isBadRequest());

        String viewerToken=login("viewer",VIEWER_PASSWORD);
        mockMvc.perform(get("/api/v1/world-versions/"+versionId+"/graph").header("Authorization","Bearer "+viewerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entities.length()").value(2)).andExpect(jsonPath("$.relationships.length()").value(1));
        mockMvc.perform(post("/api/v1/world-versions/"+versionId+"/graph/entities").header("Authorization","Bearer "+viewerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesTheWholeFamily() throws Exception {
        JsonNode first = loginResponse("viewer", VIEWER_PASSWORD);
        String oldRefreshToken = first.get("refreshToken").asText();

        String refreshedBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(oldRefreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String newRefreshToken = objectMapper.readTree(refreshedBody).get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(oldRefreshToken))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(newRefreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshFamilyAndBlacklistsAccessToken() throws Exception {
        JsonNode login = loginResponse("viewer", VIEWER_PASSWORD);
        String accessToken = login.get("accessToken").asText();
        String refreshToken = login.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String password) throws Exception {
        return loginResponse(username, password).get("accessToken").asText();
    }

    private JsonNode loginResponse(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }
}
