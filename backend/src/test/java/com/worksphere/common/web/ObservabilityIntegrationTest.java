package com.worksphere.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.worksphere.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The operational surface (spec section 25): probes, protected metrics, correlation ids. */
@AutoConfigureMockMvc
// Spring Boot switches metric exporters off in tests unless asked.
@AutoConfigureObservability
@TestPropertySource(properties = "app.observability.metrics-token=test-metrics-token")
class ObservabilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.worksphere.identity.JwtService jwtService;

    @Test
    void livenessAndReadinessProbesAreOpenAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UP")));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UP")));
    }

    @Test
    void anonymousHealthShowsOnlyTheStatusNeverTheDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UP")))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void metricsAreClosedWithoutTheScrapeToken() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer nope"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env").header("Authorization", "Bearer nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metricsWithTheTokenCoverTheAppTheDatabaseAndTheOutbox() throws Exception {
        // Make at least one request so the HTTP latency histogram has data.
        mockMvc.perform(get("/health")).andExpect(status().isOk());

        String metrics = mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer test-metrics-token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(metrics)
                .contains("jvm_memory_used_bytes")
                .contains("hikaricp_connections_active") // database connection pool
                .contains("http_server_requests_seconds") // request latency and error rate
                .contains("worksphere_outbox_unpublished") // event delivery backlog
                .contains("worksphere_outbox_oldest_age_seconds")
                .contains("worksphere_redis_up 1")
                .contains("application=\"worksphere-backend\"");
        assertThat(metrics).as("kafka client metrics (consumer lag) are exported").contains("kafka_consumer");
    }

    @Test
    void aSafeClientRequestIdIsEchoedAndAnUnsafeOneIsReplaced() throws Exception {
        mockMvc.perform(get("/health").header("X-Request-Id", "trace-12345.abc_def"))
                .andExpect(header().string("X-Request-Id", "trace-12345.abc_def"));

        String replaced = mockMvc.perform(get("/health").header("X-Request-Id", "evil id with spaces <script>"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", not(containsString("evil"))))
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(replaced).matches("[0-9a-f-]{36}");

        String tooLong = "a".repeat(200);
        assertThat(mockMvc.perform(get("/health").header("X-Request-Id", tooLong)).andReturn().getResponse()
                        .getHeader("X-Request-Id"))
                .isNotEqualTo(tooLong);

        mockMvc.perform(get("/health")).andExpect(content().string(containsString("UP")));
    }

    @Test
    void routingMistakesAreClientErrorsNotServerErrors() throws Exception {
        String auth = "Bearer " + jwtService.generateAccessToken(java.util.UUID.randomUUID());

        // Unknown URL: 404, not a 500 that would also count as a server-error alert.
        mockMvc.perform(get("/api/v1/this-does-not-exist").header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("NOT_FOUND")))
                .andExpect(content().string(not(containsString("Exception"))));
        // Wrong HTTP method on a real URL.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code", equalTo("METHOD_NOT_ALLOWED")));
        // A file upload without the file part.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/tasks/" + java.util.UUID.randomUUID() + "/attachments")
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }
}
