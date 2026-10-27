package com.worksphere.organization;

import static org.hamcrest.Matchers.equalTo;
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
class InvitationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token for .*?: (\\S+) \\(dev log");

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
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(com.worksphere.common.mail.EmailService.class))
                .addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(com.worksphere.common.mail.EmailService.class))
                .detachAppender(logAppender);
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
    void ownerInvitesAndInvitedUserCanAccept() throws Exception {
        User owner = createUser("owner@example.com");
        User invitee = createUser("invitee@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc");

        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"invitee@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", equalTo("invitee@example.com")))
                .andExpect(jsonPath("$.role", equalTo("EMPLOYEE_MEMBER")));

        String rawToken = extractLoggedToken();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(invitee.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + rawToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", equalTo("invitee@example.com")))
                .andExpect(jsonPath("$.role", equalTo("EMPLOYEE_MEMBER")));

        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"someone-else@example.com\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void nonOwnerCannotInvite() throws Exception {
        User owner = createUser("owner2@example.com");
        User member = createUser("member2@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 2");

        // Make `member` an actual member first via the accept flow, so this is testing a real
        // member's insufficient permission, not a non-member.
        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"member2@example.com\"}"))
                .andExpect(status().isCreated());
        String rawToken = extractLoggedToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"someone@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("MISSING_PERMISSION")));
    }

    @Test
    void acceptRejectsTokenWhenAccountEmailDoesNotMatchInvite() throws Exception {
        User owner = createUser("owner3@example.com");
        User wrongAccount = createUser("wrong-account@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 3");

        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"intended-invitee@example.com\"}"))
                .andExpect(status().isCreated());
        String rawToken = extractLoggedToken();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(wrongAccount.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + rawToken + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_INVITATION_TOKEN")));
    }

    @Test
    void inviteRejectsRoleThatIsNotOrganizationAssignable() throws Exception {
        User owner = createUser("owner4@example.com");
        UUID organizationId = createOrganizationAs(owner.getId(), "Acme Inc 4");

        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"someone@example.com\", \"role\": \"PLATFORM_ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_ROLE")));
    }

    @Test
    void acceptRejectsGarbageToken() throws Exception {
        User user = createUser("garbage-accept@example.com");

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"not-a-real-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_INVITATION_TOKEN")));
    }

    private String extractLoggedToken() {
        for (ILoggingEvent event : logAppender.list) {
            Matcher matcher = TOKEN_PATTERN.matcher(event.getFormattedMessage());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("Invitation token was not logged as expected");
    }
}
