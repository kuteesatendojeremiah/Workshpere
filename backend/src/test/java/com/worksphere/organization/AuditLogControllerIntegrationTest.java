package com.worksphere.organization;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
class AuditLogControllerIntegrationTest extends AbstractIntegrationTest {

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

    @Test
    void ownerCanViewAuditLogsIncludingOrganizationCreated() throws Exception {
        User owner = createUser("auditowner@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc");

        mockMvc.perform(get("/api/v1/organizations/{id}/audit-logs", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].action", equalTo("ORGANIZATION_CREATED")));
    }

    @Test
    void filteringByActionWorks() throws Exception {
        User owner = createUser("auditowner2@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 2");

        mockMvc.perform(post("/api/v1/organizations/{id}/teams", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Engineering\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/organizations/{id}/audit-logs", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .param("action", "TEAM_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.content[0].action", equalTo("TEAM_CREATED")));
    }

    @Test
    void nonOwnerMemberCannotViewAuditLogs() throws Exception {
        User owner = createUser("auditowner3@example.com");
        User member = createUser("auditmember3@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 3");

        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"auditmember3@example.com\"}"))
                .andExpect(status().isCreated());
        String rawToken = extractInvitationToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/organizations/{id}/audit-logs", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("MISSING_PERMISSION")));
    }

    @Test
    void ownerOfOrgACannotSeeOrgBsAuditLogs() throws Exception {
        User ownerA = createUser("auditowner-a@example.com");
        User ownerB = createUser("auditowner-b@example.com");
        UUID orgAId = createOrganizationAs(ownerA.getId(), "Org A");
        createOrganizationAs(ownerB.getId(), "Org B");

        mockMvc.perform(get("/api/v1/organizations/{id}/audit-logs", orgAId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(ownerA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.content[0].action", equalTo("ORGANIZATION_CREATED")));
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
