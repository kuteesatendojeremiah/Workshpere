package com.worksphere.project;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ProjectControllerIntegrationTest extends AbstractIntegrationTest {

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
    void creatorCanCreateAndReadProjectAsMember() throws Exception {
        User owner = createUser("projectowner@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc");

        UUID projectId = createProjectAs(organizationId, owner.getId(), "Website Revamp");

        mockMvc.perform(get("/api/v1/organizations/{orgId}/projects/{projectId}", organizationId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Website Revamp")))
                .andExpect(jsonPath("$.status", equalTo("ACTIVE")));
    }

    @Test
    void memberCannotSeeProjectTheyWereNotAddedTo() throws Exception {
        User owner = createUser("projectowner2@example.com");
        User member = createUser("projectmember2@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 2");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Private Project");
        inviteAndAccept(organizationId, owner.getId(), member);

        mockMvc.perform(get("/api/v1/organizations/{orgId}/projects/{projectId}", organizationId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PROJECT_NOT_FOUND")));
    }

    @Test
    void addingMemberGrantsThemVisibility() throws Exception {
        User owner = createUser("projectowner3@example.com");
        User member = createUser("projectmember3@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 3");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Team Project");
        inviteAndAccept(organizationId, owner.getId(), member);

        mockMvc.perform(post("/api/v1/organizations/{orgId}/projects/{projectId}/members", organizationId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + member.getId() + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/organizations/{orgId}/projects/{projectId}", organizationId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void removingAMemberListsThemOutAndTakesAwayVisibility() throws Exception {
        User owner = createUser("projectowner-rm@example.com");
        User member = createUser("projectmember-rm@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc RM");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Removal Project");
        inviteAndAccept(organizationId, owner.getId(), member);
        mockMvc.perform(post("/api/v1/organizations/{orgId}/projects/{projectId}/members", organizationId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + member.getId() + "\"}"))
                .andExpect(status().isCreated());

        // Owner and the new member are both listed.
        mockMvc.perform(get("/api/v1/organizations/{orgId}/projects/{projectId}/members", organizationId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", equalTo(2)));

        // A plain member may not remove people.
        mockMvc.perform(delete("/api/v1/organizations/{orgId}/projects/{projectId}/members/{userId}",
                                organizationId, projectId, owner.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/organizations/{orgId}/projects/{projectId}/members/{userId}",
                                organizationId, projectId, member.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/v1/organizations/{orgId}/projects/{projectId}/members", organizationId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(jsonPath("$.content.length()", equalTo(1)));
        mockMvc.perform(get("/api/v1/organizations/{orgId}/projects/{projectId}", organizationId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanListAndRenameProjectsButMemberCannotRename() throws Exception {
        User owner = createUser("projectowner-up@example.com");
        User member = createUser("projectmember-up@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc UP");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "Old name");
        inviteAndAccept(organizationId, owner.getId(), member);

        mockMvc.perform(put("/api/v1/organizations/{orgId}/projects/{projectId}", organizationId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"New name\", \"description\": \"Fresh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("New name")));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/projects", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name", equalTo("New name")));

        // A member who was never added to the project sees an empty list, not someone else's data.
        mockMvc.perform(get("/api/v1/organizations/{orgId}/projects", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", equalTo(0)));
    }

    @Test
    void projectFromOrgBIsNotFoundUnderOrgA() throws Exception {
        User userA = createUser("crossproject-a@example.com");
        User userB = createUser("crossproject-b@example.com");
        UUID orgAId = createOrganizationAs(userA.getId(), "Org A");
        UUID orgBId = createOrganizationAs(userB.getId(), "Org B");
        UUID projectInOrgB = createProjectAs(orgBId, userB.getId(), "Org B Project");

        mockMvc.perform(get("/api/v1/organizations/{orgId}/projects/{projectId}", orgAId, projectInOrgB)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userA.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PROJECT_NOT_FOUND")));
    }

    @Test
    void archiveChangesStatusAndRequiresManagePermission() throws Exception {
        User owner = createUser("projectowner4@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 4");
        UUID projectId = createProjectAs(organizationId, owner.getId(), "To Archive");

        mockMvc.perform(post(
                        "/api/v1/organizations/{orgId}/projects/{projectId}/archive", organizationId, projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("ARCHIVED")));
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
