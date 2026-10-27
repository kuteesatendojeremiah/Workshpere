package com.worksphere.common.ratelimit;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.worksphere.AbstractIntegrationTest;
import com.worksphere.identity.JwtService;
import com.worksphere.identity.User;
import com.worksphere.identity.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tiny limits and a private Redis key prefix, so these tests neither affect nor are affected by
 * the other integration tests that share the same Redis container.
 */
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.rate-limit.enabled=true",
            "app.rate-limit.key-prefix=ratelimit-test",
            "app.rate-limit.api-per-minute=5",
            "app.rate-limit.auth-per-minute=3",
            "app.rate-limit.login-failures-before-lockout=1000"
        })
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Test
    void anonymousCallersOfAuthEndpointsAreLimitedByIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"nobody" + i + "@example.com\", \"password\": \"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody9@example.com\", \"password\": \"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code", equalTo("RATE_LIMITED")));
    }

    @Test
    void signedInCallersAreLimitedPerUserAndOneUserDoesNotAffectAnother() throws Exception {
        User noisy = createUser("noisy@example.com");
        User quiet = createUser("quiet@example.com");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, bearer(noisy)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, bearer(noisy)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", equalTo("RATE_LIMITED")));

        // The quiet user shares the IP address but has their own budget.
        mockMvc.perform(get("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, bearer(quiet)))
                .andExpect(status().isOk());
    }

    private User createUser(String email) {
        return userRepository.save(new User(email, passwordEncoder.encode("password123"), "Test User"));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user.getId());
    }
}
