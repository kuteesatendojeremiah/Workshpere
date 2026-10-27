package com.worksphere.task;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The Redis-cached project summary: correct numbers, real caching, invalidation, no leaks. */
@AutoConfigureMockMvc
class TaskSummaryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StringRedisTemplate redis;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void summaryCountsTasksPerColumnIsCachedAndRefreshedWhenTasksChange() throws Exception {
        User owner = createUser("summary-owner@example.com");
        UUID orgId = idOf(post("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Summary Org\"}"));
        UUID projectId = idOf(post("/api/v1/organizations/{o}/projects", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Summary Project\"}"));
        UUID taskA = createTask(owner, projectId, "A");
        createTask(owner, projectId, "B");

        getSummary(owner, projectId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", equalTo(2)))
                .andExpect(jsonPath("$.countsByStatus.TODO", equalTo(2)))
                .andExpect(jsonPath("$.countsByStatus.DONE", equalTo(0)));

        // It really is in Redis now (a cache that is never filled would pass the checks above).
        assertThat(redis.hasKey("ws:cache:task-summary:" + projectId)).isTrue();

        // A status change must evict the cached value, so the next read shows the new numbers.
        mockMvc.perform(post("/api/v1/tasks/{t}/status", taskA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_PROGRESS\", \"version\": 0}"))
                .andExpect(status().isOk());
        getSummary(owner, projectId)
                .andExpect(jsonPath("$.countsByStatus.TODO", equalTo(1)))
                .andExpect(jsonPath("$.countsByStatus.IN_PROGRESS", equalTo(1)));

        // So must a new task.
        createTask(owner, projectId, "C");
        getSummary(owner, projectId)
                .andExpect(jsonPath("$.total", equalTo(3)))
                .andExpect(jsonPath("$.countsByStatus.TODO", equalTo(2)));
    }

    @Test
    void aCachedSummaryIsNeverServedToSomeoneWhoCannotSeeTheProject() throws Exception {
        User owner = createUser("summary-owner2@example.com");
        User outsider = createUser("summary-outsider@example.com");
        UUID orgId = idOf(post("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Private Org\"}"));
        UUID projectId = idOf(post("/api/v1/organizations/{o}/projects", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Private Project\"}"));
        createTask(owner, projectId, "Secret");

        getSummary(owner, projectId).andExpect(status().isOk()); // fills the cache
        assertThat(redis.hasKey("ws:cache:task-summary:" + projectId)).isTrue();

        getSummary(outsider, projectId).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions getSummary(User user, UUID projectId)
            throws Exception {
        return mockMvc.perform(get("/api/v1/projects/{p}/task-summary", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private UUID createTask(User user, UUID projectId, String title) throws Exception {
        return idOf(post("/api/v1/projects/{p}/tasks", projectId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"" + title + "\"}"));
    }

    private UUID idOf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private User createUser(String email) {
        return userRepository.save(new User(email, passwordEncoder.encode("password123"), "Test User"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user.getId());
    }
}
