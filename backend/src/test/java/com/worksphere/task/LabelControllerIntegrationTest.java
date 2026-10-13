package com.worksphere.task;

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
class LabelControllerIntegrationTest extends AbstractIntegrationTest {

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

    private UUID createLabelAs(UUID projectId, UUID userId, String name, String color) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{id}/labels", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"color\": \"" + color + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    @Test
    void createListAndDeleteLabel() throws Exception {
        User owner = createUser("labelowner@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website");

        UUID labelId = createLabelAs(projectId, owner.getId(), "Bug", "#FF0000");

        mockMvc.perform(get("/api/v1/projects/{id}/labels", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", equalTo("Bug")));

        mockMvc.perform(delete("/api/v1/projects/{projectId}/labels/{labelId}", projectId, labelId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/{id}/labels", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void duplicateLabelNameIsRejected() throws Exception {
        User owner = createUser("labelowner2@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 2");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 2");
        createLabelAs(projectId, owner.getId(), "Bug", "#FF0000");

        mockMvc.perform(post("/api/v1/projects/{id}/labels", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Bug\", \"color\": \"#00FF00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("DUPLICATE_LABEL_NAME")));
    }

    @Test
    void attachAndDetachLabelOnTask() throws Exception {
        User owner = createUser("labelowner3@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 3");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 3");
        UUID taskId = createTaskAs(projectId, owner.getId(), "Fix bug");
        UUID labelId = createLabelAs(projectId, owner.getId(), "Bug", "#FF0000");

        mockMvc.perform(post("/api/v1/tasks/{id}/labels", taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labelId\": \"" + labelId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", equalTo("Bug")));

        mockMvc.perform(get("/api/v1/tasks/{id}/labels", taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", equalTo("Bug")));

        mockMvc.perform(delete("/api/v1/tasks/{taskId}/labels/{labelId}", taskId, labelId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tasks/{id}/labels", taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }
}
