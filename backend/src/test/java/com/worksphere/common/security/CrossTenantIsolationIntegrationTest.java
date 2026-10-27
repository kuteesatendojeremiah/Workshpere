package com.worksphere.common.security;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Tenant isolation matrix (spec section 24.1, ADR-003). Two organizations exist: "victim" (A) and
 * "attacker" (B). B's owner has a perfectly valid token and is a full owner of B, but is NOT a
 * member of A. Every route that touches A's data is called with B's token, and each one must
 * answer 403 or 404, never leak A's data, and never change anything.
 *
 * <p>This is one test on purpose: the list of routes IS the checklist. When a new tenant-owned
 * endpoint is added, add a line to {@link #attackRequests}.
 */
@AutoConfigureMockMvc
class CrossTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "TopSecretVictimData";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Everything the victim organization owns. */
    private record Victim(
            User owner, UUID orgId, UUID projectId, UUID taskId, UUID sprintId, UUID teamId, UUID labelId,
            UUID commentId) {
    }

    @Test
    void anOwnerOfAnotherOrganizationCanNeitherReadNorChangeAnythingThatBelongsToTheVictim() throws Exception {
        Victim v = createVictim();
        User attacker = createUser("attacker@example.com");
        UUID attackerOrgId = idOf(post("/api/v1/organizations")
                .header(HttpHeaders.AUTHORIZATION, bearer(attacker))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Attacker Org\"}"));
        String attackerToken = bearer(attacker);

        List<MockHttpServletRequestBuilder> attacks = attackRequests(v, attackerOrgId, attacker);
        assertThat(attacks).as("the matrix must not silently shrink").hasSizeGreaterThanOrEqualTo(40);

        for (MockHttpServletRequestBuilder attack : attacks) {
            MvcResult result = mockMvc.perform(attack.header(HttpHeaders.AUTHORIZATION, attackerToken))
                    .andReturn();
            String description = result.getRequest().getMethod() + " " + result.getRequest().getRequestURI();
            assertThat(result.getResponse().getStatus())
                    .as("%s must be denied, was: %s", description, result.getResponse().getContentAsString())
                    .isIn(403, 404);
            assertThat(result.getResponse().getContentAsString())
                    .as("%s must not leak the victim's data", description)
                    .doesNotContain(SECRET);
        }

        // Nothing the attacker tried may have changed the victim's data.
        String ownerToken = bearer(v.owner);
        mockMvc.perform(get("/api/v1/organizations/{o}/projects/{p}", v.orgId, v.projectId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo(SECRET + " Project")))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")));
        mockMvc.perform(get("/api/v1/tasks/{t}", v.taskId).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", equalTo(SECRET + " Task")))
                .andExpect(jsonPath("$.status", equalTo("TODO")));
        mockMvc.perform(get("/api/v1/organizations/{o}/teams/{t}", v.orgId, v.teamId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo(SECRET + " Team")));
        mockMvc.perform(get("/api/v1/sprints/{s}", v.sprintId).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("PLANNED")));
        mockMvc.perform(get("/api/v1/tasks/{t}/comments", v.taskId).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", equalTo(1)));
        mockMvc.perform(get("/api/v1/projects/{p}/labels", v.projectId).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk());
    }

    private List<MockHttpServletRequestBuilder> attackRequests(Victim v, UUID attackerOrgId, User attacker) {
        List<MockHttpServletRequestBuilder> r = new ArrayList<>();
        String json = MediaType.APPLICATION_JSON_VALUE;

        // --- Organization-scoped routes, aimed at the victim's organization id.
        r.add(get("/api/v1/organizations/{o}", v.orgId));
        r.add(get("/api/v1/organizations/{o}/members", v.orgId));
        r.add(get("/api/v1/organizations/{o}/audit-logs", v.orgId));
        r.add(post("/api/v1/organizations/{o}/invitations", v.orgId).contentType(json)
                .content("{\"email\": \"intruder@example.com\"}"));
        r.add(get("/api/v1/organizations/{o}/projects", v.orgId));
        r.add(post("/api/v1/organizations/{o}/projects", v.orgId).contentType(json)
                .content("{\"name\": \"Planted\"}"));
        r.add(get("/api/v1/organizations/{o}/projects/{p}", v.orgId, v.projectId));
        r.add(put("/api/v1/organizations/{o}/projects/{p}", v.orgId, v.projectId).contentType(json)
                .content("{\"name\": \"Hijacked\"}"));
        r.add(post("/api/v1/organizations/{o}/projects/{p}/archive", v.orgId, v.projectId));
        r.add(get("/api/v1/organizations/{o}/projects/{p}/members", v.orgId, v.projectId));
        r.add(post("/api/v1/organizations/{o}/projects/{p}/members", v.orgId, v.projectId).contentType(json)
                .content("{\"userId\": \"" + attacker.getId() + "\"}"));
        r.add(delete("/api/v1/organizations/{o}/projects/{p}/members/{u}", v.orgId, v.projectId, v.owner.getId()));
        r.add(get("/api/v1/organizations/{o}/teams", v.orgId));
        r.add(post("/api/v1/organizations/{o}/teams", v.orgId).contentType(json)
                .content("{\"name\": \"Planted team\"}"));
        r.add(get("/api/v1/organizations/{o}/teams/{t}", v.orgId, v.teamId));
        r.add(put("/api/v1/organizations/{o}/teams/{t}", v.orgId, v.teamId).contentType(json)
                .content("{\"name\": \"Hijacked team\"}"));
        r.add(delete("/api/v1/organizations/{o}/teams/{t}", v.orgId, v.teamId));
        r.add(get("/api/v1/organizations/{o}/teams/{t}/members", v.orgId, v.teamId));
        r.add(post("/api/v1/organizations/{o}/teams/{t}/members", v.orgId, v.teamId).contentType(json)
                .content("{\"userId\": \"" + attacker.getId() + "\"}"));
        r.add(delete("/api/v1/organizations/{o}/teams/{t}/members/{u}", v.orgId, v.teamId, v.owner.getId()));

        // --- Confused deputy: the attacker's OWN org id in the path, the victim's resource id.
        r.add(get("/api/v1/organizations/{o}/projects/{p}", attackerOrgId, v.projectId));
        r.add(put("/api/v1/organizations/{o}/projects/{p}", attackerOrgId, v.projectId).contentType(json)
                .content("{\"name\": \"Hijacked\"}"));
        r.add(post("/api/v1/organizations/{o}/projects/{p}/archive", attackerOrgId, v.projectId));
        r.add(get("/api/v1/organizations/{o}/teams/{t}", attackerOrgId, v.teamId));
        r.add(put("/api/v1/organizations/{o}/teams/{t}", attackerOrgId, v.teamId).contentType(json)
                .content("{\"name\": \"Hijacked team\"}"));
        r.add(delete("/api/v1/organizations/{o}/teams/{t}", attackerOrgId, v.teamId));

        // --- Project-scoped routes (no organization id in the path: the server must derive it).
        r.add(get("/api/v1/projects/{p}/tasks", v.projectId));
        r.add(post("/api/v1/projects/{p}/tasks", v.projectId).contentType(json)
                .content("{\"title\": \"Planted task\"}"));
        r.add(get("/api/v1/projects/{p}/labels", v.projectId));
        r.add(post("/api/v1/projects/{p}/labels", v.projectId).contentType(json)
                .content("{\"name\": \"planted\", \"color\": \"#ff0000\"}"));
        r.add(delete("/api/v1/projects/{p}/labels/{l}", v.projectId, v.labelId));
        r.add(get("/api/v1/projects/{p}/sprints", v.projectId));
        r.add(post("/api/v1/projects/{p}/sprints", v.projectId).contentType(json)
                .content("{\"name\": \"Planted\", \"startDate\": \"2026-03-01\", \"endDate\": \"2026-03-14\"}"));
        r.add(get("/api/v1/projects/{p}/backlog", v.projectId));

        // --- Task-scoped routes.
        r.add(get("/api/v1/tasks/{t}", v.taskId));
        r.add(put("/api/v1/tasks/{t}", v.taskId).contentType(json)
                .content("{\"title\": \"Hijacked\", \"version\": 0}"));
        r.add(post("/api/v1/tasks/{t}/status", v.taskId).contentType(json)
                .content("{\"status\": \"IN_PROGRESS\"}"));
        r.add(post("/api/v1/tasks/{t}/assignee", v.taskId).contentType(json)
                .content("{\"assigneeId\": \"" + attacker.getId() + "\"}"));
        r.add(delete("/api/v1/tasks/{t}", v.taskId));
        r.add(get("/api/v1/tasks/{t}/dependencies", v.taskId));
        r.add(get("/api/v1/tasks/{t}/comments", v.taskId));
        r.add(post("/api/v1/tasks/{t}/comments", v.taskId).contentType(json)
                .content("{\"body\": \"planted comment\"}"));
        r.add(delete("/api/v1/tasks/{t}/comments/{c}", v.taskId, v.commentId));
        r.add(get("/api/v1/tasks/{t}/labels", v.taskId));
        r.add(post("/api/v1/tasks/{t}/labels", v.taskId).contentType(json)
                .content("{\"labelId\": \"" + v.labelId + "\"}"));

        // --- Sprint-scoped routes.
        r.add(get("/api/v1/sprints/{s}", v.sprintId));
        r.add(post("/api/v1/sprints/{s}/start", v.sprintId));
        r.add(post("/api/v1/sprints/{s}/complete", v.sprintId));
        r.add(get("/api/v1/sprints/{s}/tasks", v.sprintId));
        r.add(post("/api/v1/sprints/{s}/tasks", v.sprintId).contentType(json)
                .content("{\"taskId\": \"" + v.taskId + "\"}"));
        r.add(get("/api/v1/sprints/{s}/velocity", v.sprintId));
        r.add(get("/api/v1/sprints/{s}/burndown", v.sprintId));
        return r;
    }

    private Victim createVictim() throws Exception {
        User owner = createUser("victim-owner@example.com");
        String auth = bearer(owner);
        String json = MediaType.APPLICATION_JSON_VALUE;

        UUID orgId = idOf(post("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(json).content("{\"name\": \"Victim Org\"}"));
        UUID projectId = idOf(post("/api/v1/organizations/{o}/projects", orgId)
                .header(HttpHeaders.AUTHORIZATION, auth).contentType(json)
                .content("{\"name\": \"" + SECRET + " Project\"}"));
        UUID taskId = idOf(post("/api/v1/projects/{p}/tasks", projectId)
                .header(HttpHeaders.AUTHORIZATION, auth).contentType(json)
                .content("{\"title\": \"" + SECRET + " Task\"}"));
        UUID sprintId = idOf(post("/api/v1/projects/{p}/sprints", projectId)
                .header(HttpHeaders.AUTHORIZATION, auth).contentType(json)
                .content("{\"name\": \"Sprint 1\", \"startDate\": \"2026-01-01\", \"endDate\": \"2026-01-14\"}"));
        UUID teamId = idOf(post("/api/v1/organizations/{o}/teams", orgId)
                .header(HttpHeaders.AUTHORIZATION, auth).contentType(json)
                .content("{\"name\": \"" + SECRET + " Team\"}"));
        UUID labelId = idOf(post("/api/v1/projects/{p}/labels", projectId)
                .header(HttpHeaders.AUTHORIZATION, auth).contentType(json)
                .content("{\"name\": \"bug\", \"color\": \"#00ff00\"}"));
        UUID commentId = idOf(post("/api/v1/tasks/{t}/comments", taskId)
                .header(HttpHeaders.AUTHORIZATION, auth).contentType(json)
                .content("{\"body\": \"" + SECRET + " comment\"}"));
        return new Victim(owner, orgId, projectId, taskId, sprintId, teamId, labelId, commentId);
    }

    private UUID idOf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private User createUser(String email) {
        return userRepository.save(new User(email, passwordEncoder.encode("password123"), "Test User"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user.getId());
    }
}
