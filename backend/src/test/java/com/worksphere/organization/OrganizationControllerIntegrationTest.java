package com.worksphere.organization;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import com.worksphere.identity.JwtService;
import com.worksphere.identity.User;
import com.worksphere.identity.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class OrganizationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User createUser(String email) {
        return userRepository.save(new User(email, passwordEncoder.encode("password123"), "Test User"));
    }

    private String bearerTokenFor(UUID userId) {
        return "Bearer " + jwtService.generateAccessToken(userId);
    }

    private UUID createOrganizationAs(UUID userId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    @Test
    void creatorBecomesOwnerAndCanReadTheOrganization() throws Exception {
        User creator = createUser("creator@example.com");

        UUID organizationId = createOrganizationAs(creator.getId(), "Acme Inc");

        mockMvc.perform(get("/api/v1/organizations/{id}", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(creator.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Acme Inc")));

        mockMvc.perform(get("/api/v1/organizations/{id}/members", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(creator.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email", equalTo("creator@example.com")))
                .andExpect(jsonPath("$.content[0].role", equalTo("ORGANIZATION_OWNER")))
                .andExpect(jsonPath("$.totalElements", equalTo(1)));
    }

    @Test
    void listMineReturnsOnlyOrganizationsTheCallerBelongsTo() throws Exception {
        User userA = createUser("selector-a@example.com");
        User userB = createUser("selector-b@example.com");
        createOrganizationAs(userA.getId(), "A's Org 1");
        createOrganizationAs(userA.getId(), "A's Org 2");
        createOrganizationAs(userB.getId(), "B's Org");

        mockMvc.perform(get("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(2)));
    }

    @Test
    void userFromOrgACannotReadOrgB() throws Exception {
        User userA = createUser("user-a@example.com");
        User userB = createUser("user-b@example.com");

        createOrganizationAs(userA.getId(), "Org A");
        UUID orgBId = createOrganizationAs(userB.getId(), "Org B");

        mockMvc.perform(get("/api/v1/organizations/{id}", orgBId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userA.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ORGANIZATION_NOT_FOUND")));

        mockMvc.perform(get("/api/v1/organizations/{id}/members", orgBId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userA.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ORGANIZATION_NOT_FOUND")));
    }

    @Test
    void creatingOrganizationWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"No Auth Org\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHENTICATED")));
    }

    @Test
    void createOrganizationRejectsBlankName() throws Exception {
        User creator = createUser("blankname@example.com");

        mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(creator.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void nonexistentOrganizationLooksIdenticalToNoMembership() throws Exception {
        User user = createUser("lookup@example.com");

        mockMvc.perform(get("/api/v1/organizations/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ORGANIZATION_NOT_FOUND")));
    }
}
