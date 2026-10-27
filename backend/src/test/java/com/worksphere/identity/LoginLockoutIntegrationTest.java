package com.worksphere.identity;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.worksphere.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Spec 24.1: repeated wrong passwords lock the account's sign-in for a while, even for the right password. */
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.rate-limit.enabled=true",
            "app.rate-limit.key-prefix=lockout-test",
            "app.rate-limit.api-per-minute=100000",
            "app.rate-limit.auth-per-minute=100000",
            "app.rate-limit.login-failures-before-lockout=3"
        })
class LoginLockoutIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void afterTooManyWrongPasswordsEvenTheRightPasswordIsRefusedUntilTheLockoutEnds() throws Exception {
        createUser("lockout@example.com", "correct-password");

        for (int i = 0; i < 3; i++) {
            login("lockout@example.com", "wrong-password").andExpect(status().isUnauthorized());
        }

        login("lockout@example.com", "correct-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", equalTo("RATE_LIMITED")));
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCount() throws Exception {
        createUser("reset@example.com", "correct-password");

        login("reset@example.com", "wrong-password").andExpect(status().isUnauthorized());
        login("reset@example.com", "wrong-password").andExpect(status().isUnauthorized());
        login("reset@example.com", "correct-password").andExpect(status().isOk());

        // Two more wrong attempts would have locked the account if the earlier two still counted.
        login("reset@example.com", "wrong-password").andExpect(status().isUnauthorized());
        login("reset@example.com", "wrong-password").andExpect(status().isUnauthorized());
        login("reset@example.com", "correct-password").andExpect(status().isOk());
    }

    @Test
    void lockoutIsPerAccountAndIsNotCaseSensitive() throws Exception {
        createUser("victim@example.com", "correct-password");
        createUser("bystander@example.com", "correct-password");

        login("victim@example.com", "wrong-password").andExpect(status().isUnauthorized());
        login("VICTIM@example.com", "wrong-password").andExpect(status().isUnauthorized());
        login("Victim@Example.com", "wrong-password").andExpect(status().isUnauthorized());

        login("victim@example.com", "correct-password").andExpect(status().isTooManyRequests());
        login("bystander@example.com", "correct-password").andExpect(status().isOk());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"));
    }

    private void createUser(String email, String password) {
        userRepository.save(new User(email, passwordEncoder.encode(password), "Test User"));
    }
}
