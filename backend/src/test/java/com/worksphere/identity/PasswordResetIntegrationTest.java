package com.worksphere.identity;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token for .*?: (\\S+) \\(dev log");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(com.worksphere.common.mail.EmailService.class)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(com.worksphere.common.mail.EmailService.class)).detachAppender(logAppender);
    }

    private User createUser(String email, String rawPassword) {
        return userRepository.save(new User(email, passwordEncoder.encode(rawPassword), "Test User"));
    }

    @Test
    void resetPasswordChangesPasswordAndRevokesExistingSessions() throws Exception {
        createUser("reset-flow@example.com", "old-password");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"reset-flow@example.com\", \"password\": \"old-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String refreshTokenFromBeforeReset = readField(loginResult, "refreshToken");

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"reset-flow@example.com\"}"))
                .andExpect(status().isAccepted());

        String resetToken = extractLoggedToken();

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + resetToken + "\", \"newPassword\": \"new-password\"}"))
                .andExpect(status().isOk());

        // Old password no longer works, new one does.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"reset-flow@example.com\", \"password\": \"old-password\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"reset-flow@example.com\", \"password\": \"new-password\"}"))
                .andExpect(status().isOk());

        // The session that existed before the reset must no longer work.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshTokenFromBeforeReset + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("INVALID_REFRESH_TOKEN")));
    }

    @Test
    void resetPasswordRequestDoesNotRevealWhetherEmailExists() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"no-such-account@example.com\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void resetPasswordConfirmRejectsGarbageToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"not-a-real-token\", \"newPassword\": \"new-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_PASSWORD_RESET_TOKEN")));
    }

    private String readField(MvcResult result, String field) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get(field).asText();
    }

    private String extractLoggedToken() {
        for (ILoggingEvent event : logAppender.list) {
            Matcher matcher = TOKEN_PATTERN.matcher(event.getFormattedMessage());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("Password reset token was not logged as expected");
    }
}
