package com.worksphere.task;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@AutoConfigureMockMvc
class CommentControllerIntegrationTest extends AbstractIntegrationTest {

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

    private String inviteAndAccept(UUID organizationId, UUID ownerId, User invitee) throws Exception {
        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + invitee.getEmail() + "\"}"))
                .andExpect(status().isCreated());
        String rawToken = extractInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(invitee.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + rawToken + "\"}"))
                .andExpect(status().isOk());
        return rawToken;
    }

    @Test
    void addAndListComments() throws Exception {
        User owner = createUser("commentowner@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website");
        UUID taskId = createTaskAs(projectId, owner.getId(), "Fix bug");

        mockMvc.perform(post("/api/v1/tasks/{id}/comments", taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"Looking into this now\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body", equalTo("Looking into this now")));

        mockMvc.perform(get("/api/v1/tasks/{id}/comments", taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(1)));
    }

    @Test
    void mentioningNonProjectMemberFails() throws Exception {
        User owner = createUser("commentowner2@example.com");
        User outsider = createUser("commentoutsider2@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 2");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 2");
        UUID taskId = createTaskAs(projectId, owner.getId(), "Fix bug");

        mockMvc.perform(post("/api/v1/tasks/{id}/comments", taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"Hey\", \"mentionedUserIds\": [\"" + outsider.getId() + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("USER_NOT_IN_PROJECT")));
    }

    @Test
    void nonAuthorMemberCannotDeleteSomeoneElsesComment() throws Exception {
        User owner = createUser("commentowner3@example.com");
        User member = createUser("commentmember3@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 3");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website 3");
        UUID taskId = createTaskAs(projectId, owner.getId(), "Fix bug");
        inviteAndAccept(organizationId, owner.getId(), member);
        mockMvc.perform(post("/api/v1/organizations/{orgId}/projects/{projectId}/members", organizationId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + member.getId() + "\"}"))
                .andExpect(status().isCreated());

        MvcResult commentResult = mockMvc.perform(post("/api/v1/tasks/{id}/comments", taskId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"Owner's comment\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID commentId = UUID.fromString(
                objectMapper.readTree(commentResult.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(delete("/api/v1/tasks/{taskId}/comments/{commentId}", taskId, commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("MISSING_PERMISSION")));
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
