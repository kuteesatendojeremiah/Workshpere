package com.worksphere.issue;

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
class IssueControllerIntegrationTest extends AbstractIntegrationTest {

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

    private UUID createProjectAs(UUID organizationId, UUID userId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations/{id}/projects", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createTaskAs(UUID projectId, UUID userId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID reportIssueAs(UUID projectId, UUID userId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    @Test
    void reportAndReadIssueWithDefaults() throws Exception {
        User reporter = createUser("issuereporter@example.com");
        UUID organizationId = createOrganizationAs(reporter.getId(), "Acme Inc");
        UUID projectId = createProjectAs(organizationId, reporter.getId(), "Website");

        UUID issueId = reportIssueAs(projectId, reporter.getId(), "Login is broken");

        mockMvc.perform(get("/api/v1/issues/{id}", issueId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(reporter.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", equalTo("Login is broken")))
                .andExpect(jsonPath("$.severity", equalTo("MEDIUM")))
                .andExpect(jsonPath("$.status", equalTo("OPEN")))
                .andExpect(jsonPath("$.reporterId", equalTo(reporter.getId().toString())));
    }

    @Test
    void filteringBySeverityAndEnvironment() throws Exception {
        User reporter = createUser("issuereporter2@example.com");
        UUID organizationId = createOrganizationAs(reporter.getId(), "Acme Inc 2");
        UUID projectId = createProjectAs(organizationId, reporter.getId(), "Website 2");
        reportIssueAs(projectId, reporter.getId(), "Minor glitch");
        mockMvc.perform(post("/api/v1/projects/{id}/issues", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(reporter.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Production outage\", \"severity\": \"CRITICAL\", \"environment\": \"PRODUCTION\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/projects/{id}/issues", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(reporter.getId()))
                        .param("severity", "CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.content[0].title", equalTo("Production outage")));
    }

    @Test
    void lifecycleStatusChangesAndLinkingATask() throws Exception {
        User reporter = createUser("issuereporter3@example.com");
        UUID organizationId = createOrganizationAs(reporter.getId(), "Acme Inc 3");
        UUID projectId = createProjectAs(organizationId, reporter.getId(), "Website 3");
        UUID taskId = createTaskAs(projectId, reporter.getId(), "Fix root cause");
        UUID issueId = reportIssueAs(projectId, reporter.getId(), "Crash on login");

        mockMvc.perform(post("/api/v1/issues/{id}/status", issueId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(reporter.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("IN_PROGRESS")));

        mockMvc.perform(post("/api/v1/issues/{id}/link-task", issueId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(reporter.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\": \"" + taskId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedTaskId", equalTo(taskId.toString())));
    }

    @Test
    void assigningToOutsiderFails() throws Exception {
        User reporter = createUser("issuereporter4@example.com");
        User outsider = createUser("issueoutsider4@example.com");
        UUID organizationId = createOrganizationAs(reporter.getId(), "Acme Inc 4");
        UUID projectId = createProjectAs(organizationId, reporter.getId(), "Website 4");
        UUID issueId = reportIssueAs(projectId, reporter.getId(), "Needs triage");

        mockMvc.perform(post("/api/v1/issues/{id}/assignee", issueId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(reporter.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + outsider.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("USER_NOT_IN_PROJECT")));
    }

    @Test
    void issueFromAnotherOrganizationIsNotFound() throws Exception {
        User userA = createUser("crossissue-a@example.com");
        User userB = createUser("crossissue-b@example.com");
        createOrganizationAs(userA.getId(), "Org A");
        UUID orgBId = createOrganizationAs(userB.getId(), "Org B");
        UUID projectBId = createProjectAs(orgBId, userB.getId(), "Org B Project");
        UUID issueInOrgB = reportIssueAs(projectBId, userB.getId(), "Org B issue");

        mockMvc.perform(get("/api/v1/issues/{id}", issueInOrgB)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userA.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ISSUE_NOT_FOUND")));
    }
}
