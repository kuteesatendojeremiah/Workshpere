package com.worksphere.common.security;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import com.worksphere.identity.JwtService;
import com.worksphere.identity.User;
import com.worksphere.identity.UserRepository;
import com.worksphere.organization.InvitationService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Spec section 24.1 negative tests that cut across modules: a plain member trying to do
 * manager-only things, forged/garbage tokens, and clients trying to smuggle in fields they must
 * not control (mass assignment). Each test asserts both the rejection AND that nothing changed.
 */
@AutoConfigureMockMvc
class NegativeSecurityIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token for .*?: (\\S+) \\(no email");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(InvitationService.class)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(InvitationService.class)).detachAppender(logAppender);
    }

    // ---------------------------------------------------------------------------------------
    // Plain member cannot do manager-only things
    // ---------------------------------------------------------------------------------------

    @Test
    void memberWithoutPermissionCannotArchiveOrEditAProjectAndNothingChanges() throws Exception {
        Fixture f = fixture("archive");

        mockMvc.perform(post("/api/v1/organizations/{o}/projects/{p}/archive", f.orgId, f.projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(f.member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("MISSING_PERMISSION")));

        mockMvc.perform(put("/api/v1/organizations/{o}/projects/{p}", f.orgId, f.projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(f.member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Hijacked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("MISSING_PERMISSION")));

        mockMvc.perform(get("/api/v1/organizations/{o}/projects/{p}", f.orgId, f.projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(f.owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")))
                .andExpect(jsonPath("$.name", equalTo("Original")));
    }

    @Test
    void memberWithoutPermissionCannotCreateProjectsOrSprints() throws Exception {
        Fixture f = fixture("create");

        mockMvc.perform(post("/api/v1/organizations/{o}/projects", f.orgId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(f.member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Sneaky\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("MISSING_PERMISSION")));

        mockMvc.perform(post("/api/v1/projects/{p}/sprints", f.projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(f.member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Sprint 1\", \"startDate\": \"2026-01-01\", \"endDate\": \"2026-01-14\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("MISSING_PERMISSION")));
    }

    // ---------------------------------------------------------------------------------------
    // Forged / invalid tokens
    // ---------------------------------------------------------------------------------------

    @Test
    void tamperedSignatureIsRejected() throws Exception {
        User user = createUser("tamper@example.com");
        String token = jwtService.generateAccessToken(user.getId());
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        expectUnauthenticated("Bearer " + tampered);
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() throws Exception {
        User user = createUser("algnone@example.com");
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + user.getId() + "\",\"exp\":4102444800}");

        expectUnauthenticated("Bearer " + header + "." + payload + ".");
    }

    @Test
    void tokenSignedWithADifferentKeyIsRejected() throws Exception {
        User user = createUser("wrongkey@example.com");
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + user.getId() + "\",\"exp\":4102444800}");

        expectUnauthenticated("Bearer " + header + "." + payload + ".bm90LXRoZS1yZWFsLXNpZ25hdHVyZQ");
    }

    @Test
    void garbageAndWrongSchemeAuthorizationHeadersAreRejected() throws Exception {
        User user = createUser("garbage@example.com");

        expectUnauthenticated("Bearer not-a-jwt");
        expectUnauthenticated("Bearer ");
        expectUnauthenticated("Basic dXNlcjpwYXNz");
        expectUnauthenticated(jwtService.generateAccessToken(user.getId())); // missing the "Bearer " prefix
    }

    @Test
    void validTokenForAUserWhoIsNotInTheOrganizationCannotReadItsProjects() throws Exception {
        Fixture f = fixture("ghost");
        String ghostToken = "Bearer " + jwtService.generateAccessToken(UUID.randomUUID());

        MvcResult result = mockMvc.perform(
                        get("/api/v1/organizations/{o}/projects/{p}", f.orgId, f.projectId)
                                .header(HttpHeaders.AUTHORIZATION, ghostToken))
                .andReturn();

        // Whatever the exact code, it must be a clean client error that leaks nothing.
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isIn(403, 404);
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .doesNotContain("Original");
    }

    // ---------------------------------------------------------------------------------------
    // Fields the client must not control (mass assignment)
    // ---------------------------------------------------------------------------------------

    @Test
    void clientSuppliedOrganizationIdInTheBodyIsIgnoredWhenCreatingAProject() throws Exception {
        Fixture f = fixture("massorg");
        Fixture other = fixture("massorgother");

        MvcResult result = mockMvc.perform(post("/api/v1/organizations/{o}/projects", f.orgId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(f.owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mine\", \"organizationId\": \"" + other.orgId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId", equalTo(f.orgId.toString())))
                .andReturn();
        String newProjectId = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();

        // The other organization's owner must not be able to see it.
        mockMvc.perform(get("/api/v1/organizations/{o}/projects/{p}", other.orgId, newProjectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientCannotSetStatusOrIdThroughTaskCreateOrUpdate() throws Exception {
        Fixture f = fixture("masstask");
        Fixture other = fixture("masstaskother");

        MvcResult created = mockMvc.perform(post("/api/v1/projects/{p}/tasks", f.projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(f.owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"T\", \"status\": \"DONE\", \"projectId\": \"" + other.projectId
                                + "\", \"assigneeId\": \"" + f.owner.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", equalTo("TODO")))
                .andExpect(jsonPath("$.projectId", equalTo(f.projectId.toString())))
                .andExpect(jsonPath("$.assigneeId", equalTo(null)))
                .andReturn();
        var task = objectMapper.readTree(created.getResponse().getContentAsString());
        String taskId = task.get("id").asText();
        long version = task.get("version").asLong();

        // A plain update must not be able to move the task between columns or projects either.
        mockMvc.perform(put("/api/v1/tasks/{t}", taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(f.owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"T2\", \"status\": \"DONE\", \"projectId\": \"" + other.projectId
                                + "\", \"version\": " + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", equalTo("T2")))
                .andExpect(jsonPath("$.status", equalTo("TODO")))
                .andExpect(jsonPath("$.projectId", equalTo(f.projectId.toString())));

        // And the other tenant cannot see it.
        mockMvc.perform(get("/api/v1/tasks/{t}", taskId).header(HttpHeaders.AUTHORIZATION, bearer(other.owner)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private void expectUnauthenticated(String authorizationHeader) throws Exception {
        mockMvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHENTICATED")))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** An org with an owner, a project named "Original", and a plain member who IS on the project. */
    private record Fixture(User owner, User member, UUID orgId, UUID projectId) {
    }

    private Fixture fixture(String tag) throws Exception {
        User owner = createUser("owner-" + tag + "@example.com");
        User member = createUser("member-" + tag + "@example.com");
        UUID orgId = idOf(post("/api/v1/organizations")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Org " + tag + "\"}"));
        UUID projectId = idOf(post("/api/v1/organizations/{o}/projects", orgId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Original\"}"));

        logAppender.list.clear();
        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", orgId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + member.getEmail() + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + extractInvitationToken() + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/organizations/{o}/projects/{p}/members", orgId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + member.getId() + "\"}"))
                .andExpect(status().isCreated());

        return new Fixture(owner, member, orgId, projectId);
    }

    private UUID idOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
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

    private String extractInvitationToken() {
        for (ILoggingEvent event : logAppender.list) {
            Matcher matcher = TOKEN_PATTERN.matcher(event.getFormattedMessage());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("Invitation token was not logged as expected");
    }
}
