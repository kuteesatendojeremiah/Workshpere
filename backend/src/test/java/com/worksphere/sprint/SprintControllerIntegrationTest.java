package com.worksphere.sprint;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import com.worksphere.identity.JwtService;
import com.worksphere.identity.User;
import com.worksphere.identity.UserRepository;
import java.time.LocalDate;
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
class SprintControllerIntegrationTest extends AbstractIntegrationTest {

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

    private UUID createSprintAs(UUID projectId, UUID userId, String name) throws Exception {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusWeeks(2);
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{id}/sprints", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"startDate\": \"" + start + "\", \"endDate\": \""
                                + end + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    @Test
    void createStartAndCompleteSprintLifecycle() throws Exception {
        User owner = createUser("sprintowner@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website");

        UUID sprintId = createSprintAs(projectId, owner.getId(), "Sprint 1");

        mockMvc.perform(get("/api/v1/sprints/{id}", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("PLANNED")));

        mockMvc.perform(post("/api/v1/sprints/{id}/start", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")));

        mockMvc.perform(post("/api/v1/sprints/{id}/complete", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("COMPLETED")));
    }

    @Test
    void cannotStartTwoActiveSprintsInSameProject() throws Exception {
        User owner = createUser("sprintowner2@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 2");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 2");
        UUID sprint1 = createSprintAs(projectId, owner.getId(), "Sprint 1");
        UUID sprint2 = createSprintAs(projectId, owner.getId(), "Sprint 2");

        mockMvc.perform(post("/api/v1/sprints/{id}/start", sprint1)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sprints/{id}/start", sprint2)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_SPRINT_STATE")));
    }

    @Test
    void addingTaskToSprintRemovesItFromBacklog() throws Exception {
        User owner = createUser("sprintowner3@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 3");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 3");
        UUID taskId = createTaskAs(projectId, owner.getId(), "Backlog task");
        UUID sprintId = createSprintAs(projectId, owner.getId(), "Sprint 1");

        mockMvc.perform(get("/api/v1/projects/{id}/backlog", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(1)));

        mockMvc.perform(post("/api/v1/sprints/{id}/tasks", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\": \"" + taskId + "\", \"storyPoints\": 5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storyPoints", equalTo(5)));

        mockMvc.perform(get("/api/v1/projects/{id}/backlog", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(0)));

        mockMvc.perform(get("/api/v1/sprints/{id}/velocity", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committedPoints", equalTo(5)))
                .andExpect(jsonPath("$.completedPoints", equalTo(0)));

        mockMvc.perform(delete("/api/v1/sprints/{sprintId}/tasks/{taskId}", sprintId, taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/{id}/backlog", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(1)));
    }

    @Test
    void burndownReflectsCompletedStoryPoints() throws Exception {
        User owner = createUser("sprintowner4@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 4");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 4");
        UUID taskId = createTaskAs(projectId, owner.getId(), "Sprint task");
        UUID sprintId = createSprintAs(projectId, owner.getId(), "Sprint 1");

        mockMvc.perform(post("/api/v1/sprints/{id}/tasks", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\": \"" + taskId + "\", \"storyPoints\": 8}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/sprints/{id}/burndown", sprintId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].remainingPoints", equalTo(8)));
    }

    @Test
    void sprintFromOrgBIsNotFoundUnderOrgA() throws Exception {
        User userA = createUser("crosssprint-a@example.com");
        User userB = createUser("crosssprint-b@example.com");
        createOrganizationAs(userA.getId(), "Org A");
        UUID orgBId = createOrganizationAs(userB.getId(), "Org B");
        UUID projectBId = createProjectAs(orgBId, userB.getId(), "Org B Project");
        UUID sprintInOrgB = createSprintAs(projectBId, userB.getId(), "Org B Sprint");

        mockMvc.perform(get("/api/v1/sprints/{id}", sprintInOrgB)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userA.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("SPRINT_NOT_FOUND")));
    }
}
