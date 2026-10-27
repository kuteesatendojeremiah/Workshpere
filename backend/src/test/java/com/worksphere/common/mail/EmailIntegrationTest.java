package com.worksphere.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import com.worksphere.identity.JwtService;
import com.worksphere.identity.User;
import com.worksphere.identity.UserRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Real SMTP (Mailpit): the account emails are sent, carry a working-looking link, and only after commit. */
@AutoConfigureMockMvc
class EmailIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void registeringSendsAVerificationEmailWithALinkToTheFrontend() throws Exception {
        String email = "newcomer-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"password123\", \"displayName\": \"New\"}"))
                .andExpect(status().isCreated());

        String body = awaitEmailTo(email, "Verify");
        assertThat(body).contains("http://localhost:5173/verify-email?token=");
    }

    @Test
    void passwordResetSendsALinkAndNeverRevealsWhetherTheAddressExists() throws Exception {
        String email = "forgetful-" + UUID.randomUUID() + "@example.com";
        userRepository.save(new User(email, passwordEncoder.encode("password123"), "Forgetful"));

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\"}"))
                .andExpect(status().is2xxSuccessful());

        assertThat(awaitEmailTo(email, "Reset")).contains("/reset-password?token=");
    }

    @Test
    void invitingSomeoneEmailsThemALinkToAcceptIt() throws Exception {
        User owner = userRepository.save(
                new User("inviter-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode("password123"), "Owner"));
        String auth = "Bearer " + jwtService.generateAccessToken(owner.getId());
        String orgId = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/organizations")
                                .header(HttpHeaders.AUTHORIZATION, auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\": \"Mail Org\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();
        String invitee = "invitee-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/organizations/{id}/invitations", orgId)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + invitee + "\"}"))
                .andExpect(status().isCreated());

        String body = awaitEmailTo(invitee, "invited");
        assertThat(body).contains("Mail Org").contains("/accept-invitation?token=");
    }

    @Test
    void noEmailIsSentWhenTheTransactionRollsBack() throws Exception {
        String email = "ghost-" + UUID.randomUUID() + "@example.com";

        // Registering twice: the second attempt fails (409) inside its transaction and must not email.
        String body = "{\"email\": \"" + email + "\", \"password\": \"password123\", \"displayName\": \"G\"}";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        awaitEmailTo(email, "Verify");
        Thread.sleep(1500); // give a (wrong) second email time to show up
        assertThat(countEmailsTo(email)).isEqualTo(1);
    }

    // --- Mailpit helpers -------------------------------------------------------------------

    private String awaitEmailTo(String address, String subjectContains) {
        String[] text = new String[1];
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            JsonNode messages = get("/api/v1/search?query=" + URLEncoder.encode("to:" + address, StandardCharsets.UTF_8))
                    .get("messages");
            assertThat(messages).as("an email to " + address).isNotEmpty();
            JsonNode first = messages.get(0);
            assertThat(first.get("Subject").asText()).contains(subjectContains);
            text[0] = get("/api/v1/message/" + first.get("ID").asText()).get("Text").asText();
        });
        return text[0];
    }

    private int countEmailsTo(String address) {
        return get("/api/v1/search?query=" + URLEncoder.encode("to:" + address, StandardCharsets.UTF_8))
                .get("messages").size();
    }

    private JsonNode get(String path) {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(mailpitUrl() + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
