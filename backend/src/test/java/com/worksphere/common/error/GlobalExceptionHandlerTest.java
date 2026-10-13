package com.worksphere.common.error;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.worksphere.identity.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest}'s reduced component scan still picks up {@link JwtAuthenticationFilter}
 * (it's a {@code Filter} bean) even with {@code addFilters = false}, so it needs mocking out
 * here purely to satisfy its constructor dependency - this slice never actually invokes it.
 */
@WebMvcTest(controllers = ValidationTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void validationFailureReturnsConsistentErrorShape() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo(ErrorCode.VALIDATION_ERROR)))
                .andExpect(jsonPath("$.path", equalTo("/test/validate")))
                .andExpect(jsonPath("$.details[0].field", equalTo("name")))
                .andExpect(jsonPath("$.details[0].message", equalTo("name must not be blank")));
    }
}
