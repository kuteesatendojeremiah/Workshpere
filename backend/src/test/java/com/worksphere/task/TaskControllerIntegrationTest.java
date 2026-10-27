package com.worksphere.task;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class TaskControllerIntegrationTest extends AbstractIntegrationTest {

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

    private JsonTask createTaskAs(UUID projectId, UUID userId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new JsonTask(UUID.fromString(json.get("id").asText()), json.get("version").asLong());
    }

    private record JsonTask(UUID id, long version) {
    }

    @Test
    void creatorCanCreateAndReadTask() throws Exception {
        User owner = createUser("taskowner@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website");

        JsonTask task = createTaskAs(projectId, owner.getId(), "Fix login bug");

        mockMvc.perform(get("/api/v1/tasks/{id}", task.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", equalTo("Fix login bug")))
                .andExpect(jsonPath("$.status", equalTo("TODO")))
                .andExpect(jsonPath("$.priority", equalTo("MEDIUM")));
    }

    @Test
    void taskFromAnotherOrganizationIsNotFoundEvenByDirectId() throws Exception {
        User userA = createUser("crosstask-a@example.com");
        User userB = createUser("crosstask-b@example.com");
        UUID orgAId = createOrganizationAs(userA.getId(), "Org A");
        UUID orgBId = createOrganizationAs(userB.getId(), "Org B");
        UUID projectBId = createProjectAs(orgBId, userB.getId(), "Org B Project");
        JsonTask taskInOrgB = createTaskAs(projectBId, userB.getId(), "Org B Task");

        // userA has no relationship whatsoever to Org B - swapping in Org B's task id must fail,
        // even though this route never mentions an organization or project id at all.
        mockMvc.perform(get("/api/v1/tasks/{id}", taskInOrgB.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userA.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("TASK_NOT_FOUND")));
    }

    @Test
    void listTasksFiltersByStatusAndPriority() throws Exception {
        User owner = createUser("tasklistowner@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 2");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 2");

        JsonTask lowTask = createTaskAs(projectId, owner.getId(), "Low priority task");
        mockMvc.perform(post("/api/v1/projects/{id}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Urgent task\", \"priority\": \"URGENT\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/projects/{id}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .param("priority", "URGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.content[0].title", equalTo("Urgent task")));

        // Status moves are one column at a time - walk TODO -> IN_PROGRESS -> IN_REVIEW -> DONE.
        long version = lowTask.version();
        for (String nextStatus : new String[] {"IN_PROGRESS", "IN_REVIEW", "DONE"}) {
            MvcResult moveResult = mockMvc.perform(post("/api/v1/tasks/{id}/status", lowTask.id())
                            .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"" + nextStatus + "\", \"version\": " + version + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", equalTo(nextStatus)))
                    .andReturn();
            version = objectMapper.readTree(moveResult.getResponse().getContentAsString()).get("version").asLong();
        }

        mockMvc.perform(get("/api/v1/projects/{id}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.content[0].title", equalTo("Low priority task")));
    }

    @Test
    void updatingWithStaleVersionIsRejected() throws Exception {
        User owner = createUser("staleowner@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 3");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 3");
        JsonTask task = createTaskAs(projectId, owner.getId(), "Original title");

        mockMvc.perform(put("/api/v1/tasks/{id}", task.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"First update\", \"version\": " + task.version() + "}"))
                .andExpect(status().isOk());

        // Reusing the original (now stale) version must fail.
        mockMvc.perform(put("/api/v1/tasks/{id}", task.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Conflicting update\", \"version\": " + task.version() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("STALE_VERSION")));
    }

    @Test
    void assigningToNonProjectMemberFails() throws Exception {
        User owner = createUser("assignowner@example.com");
        User outsider = createUser("assignoutsider@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 4");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 4");
        JsonTask task = createTaskAs(projectId, owner.getId(), "Needs assignment");

        mockMvc.perform(post("/api/v1/tasks/{id}/assignee", task.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\": \"" + outsider.getId() + "\", \"version\": " + task.version() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("USER_NOT_IN_PROJECT")));
    }

    @Test
    void skippingAColumnForwardIsRejected() throws Exception {
        User owner = createUser("kanbanskip@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc Kanban Skip");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website Skip");
        JsonTask task = createTaskAs(projectId, owner.getId(), "New task");

        mockMvc.perform(post("/api/v1/tasks/{id}/status", task.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"DONE\", \"version\": " + task.version() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_STATUS_TRANSITION")));
    }

    @Test
    void reorderingWithinAColumnIsReflectedInListOrder() throws Exception {
        User owner = createUser("kanbanorder@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc Kanban Order");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website Order");
        JsonTask first = createTaskAs(projectId, owner.getId(), "First");
        JsonTask second = createTaskAs(projectId, owner.getId(), "Second");

        // Move "Second" to the front of the TODO column.
        mockMvc.perform(post("/api/v1/tasks/{id}/status", second.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"TODO\", \"targetPosition\": 0, \"version\": " + second.version() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position", equalTo(0)));

        mockMvc.perform(get("/api/v1/projects/{id}/tasks", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .param("sort", "position,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title", equalTo("Second")))
                .andExpect(jsonPath("$.content[0].position", equalTo(0)))
                .andExpect(jsonPath("$.content[1].title", equalTo("First")))
                .andExpect(jsonPath("$.content[1].position", equalTo(1)));
    }

    @Test
    void dependenciesCanBeAddedListedAndRemoved() throws Exception {
        User owner = createUser("depowner@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 5");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 5");
        JsonTask taskA = createTaskAs(projectId, owner.getId(), "Task A");
        JsonTask taskB = createTaskAs(projectId, owner.getId(), "Task B");

        mockMvc.perform(post("/api/v1/tasks/{id}/dependencies", taskA.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dependsOnTaskId\": \"" + taskB.id() + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tasks/{id}/dependencies", taskA.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dependsOnTaskId", equalTo(taskB.id().toString())));

        mockMvc.perform(delete("/api/v1/tasks/{id}/dependencies/{depId}", taskA.id(), taskB.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tasks/{id}/dependencies", taskA.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void selfDependencyIsRejected() throws Exception {
        User owner = createUser("selfdepowner@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 6");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 6");
        JsonTask task = createTaskAs(projectId, owner.getId(), "Task A");

        mockMvc.perform(post("/api/v1/tasks/{id}/dependencies", task.id())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dependsOnTaskId\": \"" + task.id() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_TASK_DEPENDENCY")));
    }

    @Test
    void deletingATaskRemovesItAlongWithItsCommentsAndDependencies() throws Exception {
        User owner = createUser("taskdelete@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc Delete");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Delete Project");
        JsonTask doomed = createTaskAs(projectId, owner.getId(), "Doomed");
        JsonTask other = createTaskAs(projectId, owner.getId(), "Other");
        String auth = bearerTokenFor(owner.getId());

        mockMvc.perform(post("/api/v1/tasks/{id}/comments", doomed.id())
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"about to vanish\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tasks/{id}/dependencies", doomed.id())
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dependsOnTaskId\": \"" + other.id() + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/tasks/{id}", doomed.id()).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tasks/{id}", doomed.id()).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
        // The task that was depended on is untouched.
        mockMvc.perform(get("/api/v1/tasks/{id}", other.id()).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
    }
}
