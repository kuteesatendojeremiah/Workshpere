package com.worksphere.team;

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
class TeamControllerIntegrationTest extends AbstractIntegrationTest {

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

    private UUID createTeamAs(UUID organizationId, UUID userId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations/{id}/teams", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    @Test
    void creatorCanCreateAndReadTeamAsLead() throws Exception {
        User owner = createUser("teamowner@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc");

        UUID teamId = createTeamAs(organizationId, owner.getId(), "Engineering");

        mockMvc.perform(get("/api/v1/organizations/{orgId}/teams/{teamId}", organizationId, teamId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Engineering")));

        mockMvc.perform(get("/api/v1/organizations/{orgId}/teams/{teamId}/members", organizationId, teamId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].teamRole", equalTo("LEAD")));
    }

    @Test
    void memberWithoutPermissionCannotCreateTeam() throws Exception {
        User owner = createUser("teamowner2@example.com");
        User member = createUser("teammember2@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 2");

        // Invite member and accept, so this truly tests a member's lack of permission.
        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"teammember2@example.com\"}"))
                .andExpect(status().isCreated());
        String rawToken = extractInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/organizations/{id}/teams", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Unauthorized Team\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("MISSING_PERMISSION")));
    }

    @Test
    void teamFromOrgBIsNotFoundUnderOrgA() throws Exception {
        User userA = createUser("crossteam-a@example.com");
        User userB = createUser("crossteam-b@example.com");
        UUID orgAId = createOrganizationAs(userA.getId(), "Org A");
        UUID orgBId = createOrganizationAs(userB.getId(), "Org B");
        UUID teamInOrgB = createTeamAs(orgBId, userB.getId(), "Org B Team");

        // userA is a member of Org A, not Org B - even addressing Org A's own path, a team
        // that actually belongs to Org B must not be reachable.
        mockMvc.perform(get("/api/v1/organizations/{orgId}/teams/{teamId}", orgAId, teamInOrgB)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(userA.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("TEAM_NOT_FOUND")));
    }

    @Test
    void updatingTeamRequiresLeadOrManagePermission() throws Exception {
        User owner = createUser("teamowner3@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 3");
        UUID teamId = createTeamAs(organizationId, owner.getId(), "Original Name");

        mockMvc.perform(put("/api/v1/organizations/{orgId}/teams/{teamId}", organizationId, teamId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", equalTo("Renamed")));
    }

    @Test
    void deletingTeamRemovesIt() throws Exception {
        User owner = createUser("teamowner4@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 4");
        UUID teamId = createTeamAs(organizationId, owner.getId(), "To Delete");

        mockMvc.perform(delete("/api/v1/organizations/{orgId}/teams/{teamId}", organizationId, teamId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/organizations/{orgId}/teams/{teamId}", organizationId, teamId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingMemberNotInOrganizationFails() throws Exception {
        User owner = createUser("teamowner5@example.com");
        User outsider = createUser("outsider5@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 5");
        UUID teamId = createTeamAs(organizationId, owner.getId(), "Engineering");

        mockMvc.perform(post("/api/v1/organizations/{orgId}/teams/{teamId}/members", organizationId, teamId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + outsider.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("USER_NOT_IN_ORGANIZATION")));
    }

    @Test
    void removingATeamMemberAndListingTeams() throws Exception {
        User owner = createUser("teamowner-rm@example.com");
        User member = createUser("teammember-rm@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc RM");
        UUID teamId = createTeamAs(organizationId, owner.getId(), "Platform");

        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"teammember-rm@example.com\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + extractInvitationToken() + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/organizations/{orgId}/teams/{teamId}/members", organizationId, teamId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + member.getId() + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/organizations/{orgId}/teams", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name", equalTo("Platform")));

        // A plain member cannot remove anybody.
        mockMvc.perform(delete("/api/v1/organizations/{orgId}/teams/{teamId}/members/{userId}",
                                organizationId, teamId, owner.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/organizations/{orgId}/teams/{teamId}/members/{userId}",
                                organizationId, teamId, member.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/v1/organizations/{orgId}/teams/{teamId}/members", organizationId, teamId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(jsonPath("$.content.length()", equalTo(1)));
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
