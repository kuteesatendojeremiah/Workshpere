package com.worksphere.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.mock.env.MockEnvironment;

/**
 * Loads the real logback-spring.xml the way Spring Boot does, once with the "prod" profile, and checks
 * that logging switches to one JSON object per line carrying the requestId (spec section 25). Without
 * this a typo in the prod branch would only be discovered in production.
 *
 * <p>It drives Boot's {@link LoggingSystem} directly (clean-up first, because other tests in the same JVM
 * have already initialised logging) and restores the normal configuration afterwards.
 */
class JsonLoggingTest {

    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    private void configure(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), null, null);
    }

    @AfterEach
    void restoreDevelopmentLogging() {
        configure();
    }

    private static Logger root() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    @Test
    @SuppressWarnings("unchecked")
    void inTheProdProfileEveryLogLineIsAJsonObjectWithTheRequestId() throws Exception {
        configure("prod");

        assertThat(root().getAppender("JSON")).as("the prod JSON appender is configured").isNotNull();
        assertThat(root().getAppender("CONSOLE")).as("the plain-text appender is not").isNull();

        ConsoleAppender<ILoggingEvent> appender = (ConsoleAppender<ILoggingEvent>) root().getAppender("JSON");
        LoggingEvent event = new LoggingEvent(
                "test", (Logger) LoggerFactory.getLogger("http.access"), Level.INFO,
                "GET /api/v1/plans -> 200 in 12 ms", null, null);
        event.setMDCPropertyMap(Map.of("requestId", "req-abc-123", "http.status", "200"));

        String line = new String(appender.getEncoder().encode(event), StandardCharsets.UTF_8);

        JsonNode json = new ObjectMapper().readTree(line.trim());
        assertThat(json.get("message").asText()).isEqualTo("GET /api/v1/plans -> 200 in 12 ms");
        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("logger_name").asText()).isEqualTo("http.access");
        assertThat(json.get("requestId").asText()).isEqualTo("req-abc-123");
        assertThat(json.get("http.status").asText()).isEqualTo("200");
        assertThat(json.get("service").asText()).isEqualTo("worksphere-backend");
        assertThat(line.trim().split("\n")).as("exactly one line per event").hasSize(1);
    }

    @Test
    void withoutTheProdProfileLogsStayReadableTextWithTheRequestId() {
        configure();

        assertThat(root().getAppender("CONSOLE")).isNotNull();
        assertThat(root().getAppender("JSON")).isNull();
    }

    @Test
    void aStackTraceStaysInTheLogLineAndCarriesTheRootCauseFirst() throws Exception {
        configure("prod");
        @SuppressWarnings("unchecked")
        ConsoleAppender<ILoggingEvent> appender = (ConsoleAppender<ILoggingEvent>) root().getAppender("JSON");
        LoggingEvent event = new LoggingEvent(
                "test", (Logger) LoggerFactory.getLogger("example"), Level.ERROR, "boom", new IllegalStateException(
                        "outer", new IllegalArgumentException("root cause")), null);

        JsonNode json = new ObjectMapper().readTree(new String(appender.getEncoder().encode(event), StandardCharsets.UTF_8));

        assertThat(json.get("stack_trace").asText()).contains("root cause").contains("IllegalStateException");
    }
}
