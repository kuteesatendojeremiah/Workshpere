package com.worksphere.identity;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class LoginIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User createUser(String email, String rawPassword) {
        return userRepository.save(new User(email, passwordEncoder.encode(rawPassword), "Test User"));
    }

    @Test
    void loginWithCorrectPasswordSucceedsAndAccessTokenWorks() throws Exception {
        User user = createUser("login-ok@example.com", "correct-password");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"login-ok@example.com\", \"password\": \"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", equalTo("Bearer")))
                .andReturn();

        String accessToken = readField(loginResult, "accessToken");

        mockMvc.perform(get("/api/v1/users/{id}", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", equalTo("login-ok@example.com")));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        createUser("login-wrong@example.com", "correct-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"login-wrong@example.com\", \"password\": \"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("INVALID_CREDENTIALS")));
    }

    @Test
    void loginWithUnknownEmailFailsWithSameErrorAsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"no-such-user@example.com\", \"password\": \"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("INVALID_CREDENTIALS")));
    }

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        User user = createUser("expired-token@example.com", "correct-password");
        String expiredToken = jwtService.generateAccessToken(user.getId(), Duration.ofSeconds(-1));

        mockMvc.perform(get("/api/v1/users/{id}", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHENTICATED")));
    }

    @Test
    void refreshRotatesTokenAndOldOneCanNeverBeUsedAgain() throws Exception {
        createUser("refresh-rotation@example.com", "correct-password");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\": \"refresh-rotation@example.com\", \"password\": \"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String firstRefreshToken = readField(loginResult, "refreshToken");

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + firstRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andReturn();
        String secondRefreshToken = readField(refreshResult, "refreshToken");

        // Reusing the now-rotated-away first token is treated as token theft: it is rejected,
        // and as a precaution every token for this user (including the brand new second one) is revoked.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + firstRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("INVALID_REFRESH_TOKEN")));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + secondRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("INVALID_REFRESH_TOKEN")));
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        createUser("logout@example.com", "correct-password");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"logout@example.com\", \"password\": \"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = readField(loginResult, "refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("INVALID_REFRESH_TOKEN")));
    }

    private String readField(MvcResult result, String field) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get(field).asText();
    }
}
