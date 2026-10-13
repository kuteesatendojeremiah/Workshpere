package com.worksphere.identity;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.worksphere.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User createUser(String email, String displayName) {
        return userRepository.save(
                new User(email, passwordEncoder.encode("password123"), displayName));
    }

    private String bearerTokenFor(UUID userId) {
        return "Bearer " + jwtService.generateAccessToken(userId);
    }

    @Test
    void getProfileReturnsExistingUser() throws Exception {
        User user = createUser("profile@example.com", "Profile User");

        mockMvc.perform(get("/api/v1/users/{id}", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", equalTo("profile@example.com")))
                .andExpect(jsonPath("$.displayName", equalTo("Profile User")));
    }

    @Test
    void getProfileWithoutTokenReturns401() throws Exception {
        User user = createUser("notoken@example.com", "No Token");

        mockMvc.perform(get("/api/v1/users/{id}", user.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHENTICATED")));
    }

    @Test
    void getProfileForAnotherUserReturns403() throws Exception {
        User self = createUser("self@example.com", "Self");
        User other = createUser("other@example.com", "Other");

        mockMvc.perform(get("/api/v1/users/{id}", other.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(self.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("ACCESS_DENIED")));
    }

    @Test
    void getProfileReturns404ForUnknownUser() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/{id}", unknownId)
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(unknownId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("USER_NOT_FOUND")));
    }

    @Test
    void updateProfileChangesDisplayName() throws Exception {
        User user = createUser("update@example.com", "Old Name");

        mockMvc.perform(put("/api/v1/users/{id}", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", equalTo("New Name")));
    }

    @Test
    void updateProfileRejectsBlankDisplayName() throws Exception {
        User user = createUser("blank@example.com", "Some Name");

        mockMvc.perform(put("/api/v1/users/{id}", user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }
}
