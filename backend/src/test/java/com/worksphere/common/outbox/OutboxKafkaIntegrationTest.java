package com.worksphere.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import com.worksphere.identity.JwtService;
import com.worksphere.identity.User;
import com.worksphere.identity.UserRepository;
import com.worksphere.reporting.ActivityFeedConsumer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The outbox pattern end to end against real PostgreSQL and real Kafka (spec 16): a business
 * change produces an event, the relay publishes it, the consumer records it exactly once, and bad
 * messages end up in the dead-letter topic instead of blocking the queue.
 */
@AutoConfigureMockMvc
class OutboxKafkaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ActivityFeedConsumer activityFeedConsumer;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaConnectionDetails kafkaConnectionDetails;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aBusinessChangeBecomesAPublishedEventAndExactlyOneActivityRow() throws Exception {
        User owner = userRepository.save(
                new User("outbox-owner@example.com", passwordEncoder.encode("password123"), "Owner"));

        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateAccessToken(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Outbox Org\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orgId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

        // 1. The event was written in the same transaction as the organization.
        Integer queued = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'ORGANIZATION_CREATED'",
                Integer.class, orgId);
        assertThat(queued).isEqualTo(1);

        // 2. The relay publishes it and marks it published, and 3. the consumer records it once.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND published_at IS NOT NULL",
                            Integer.class, orgId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM activity_feed WHERE aggregate_id = ? "
                                    + "AND event_type = 'ORGANIZATION_CREATED' AND organization_id = ?",
                            Integer.class, orgId, orgId))
                    .isEqualTo(1);
        });
    }

    @Test
    void anEventIsNotQueuedWhenTheBusinessTransactionRollsBack() {
        UUID aggregateId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    outboxService.record(
                            DomainEventType.PROJECT_CREATED, "PROJECT", aggregateId, UUID.randomUUID(), Map.of());
                    throw new IllegalStateException("business rule failed after the event was recorded");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Integer.class, aggregateId))
                .isZero();
    }

    @Test
    void recordingAnEventOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> outboxService.record(
                        DomainEventType.PROJECT_CREATED, "PROJECT", UUID.randomUUID(), null, Map.of()))
                .hasMessageContaining("transaction");
    }

    @Test
    void handlingTheSameEventTwiceHasTheSameEffectAsOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        String message = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId.toString(),
                "eventType", "TASK_CREATED",
                "aggregateType", "TASK",
                "aggregateId", aggregateId.toString(),
                "organizationId", UUID.randomUUID().toString(),
                "occurredAt", "2026-01-01T00:00:00Z",
                "data", Map.of()));

        assertThat(activityFeedConsumer.handle(message)).isTrue();
        assertThat(activityFeedConsumer.handle(message)).as("duplicate delivery is ignored").isFalse();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM activity_feed WHERE event_id = ?", Integer.class, eventId))
                .isEqualTo(1);
    }

    @Test
    void aMessageThatCannotBeProcessedGoesToTheDeadLetterTopic() {
        String poison = "this is not json " + UUID.randomUUID();
        kafkaTemplate.send("worksphere.events", "poison-key", poison);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                String.join(",", kafkaConnectionDetails.getBootstrapServers()));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("worksphere.events.DLT"));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                boolean found = false;
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    found |= poison.equals(record.value());
                }
                assertThat(found).as("poison message arrived in the dead-letter topic").isTrue();
            });
        }
    }
}
