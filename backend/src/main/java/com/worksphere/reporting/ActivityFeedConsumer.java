package com.worksphere.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code activity_feed} table from domain events (the foundation for reporting).
 *
 * <p>IDEMPOTENT: Kafka (and our outbox relay) deliver at-least-once, so the same event can arrive
 * more than once. {@code event_id} is UNIQUE and the insert uses {@code ON CONFLICT DO NOTHING}, so
 * handling an event twice has exactly the same effect as handling it once.
 *
 * <p>A message that is not valid JSON throws {@link JsonProcessingException}, which the error
 * handler sends straight to the dead-letter topic (see {@code KafkaConfig}).
 */
@Component
public class ActivityFeedConsumer {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public ActivityFeedConsumer(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @KafkaListener(topics = "${app.outbox.topic:worksphere.events}", groupId = "worksphere-reporting")
    public void onEvent(String message) throws JsonProcessingException {
        handle(message);
    }

    /** @return true if a new row was written, false if this event had already been processed. */
    public boolean handle(String message) throws JsonProcessingException {
        JsonNode event = objectMapper.readTree(message);
        String orgId = event.path("organizationId").isNull() ? null : event.path("organizationId").asText(null);

        int inserted = jdbcTemplate.update(
                "INSERT INTO activity_feed (event_id, event_type, organization_id, aggregate_type, aggregate_id, "
                        + "occurred_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING",
                UUID.fromString(event.get("eventId").asText()),
                event.get("eventType").asText(),
                orgId == null ? null : UUID.fromString(orgId),
                event.get("aggregateType").asText(),
                UUID.fromString(event.get("aggregateId").asText()),
                Timestamp.from(Instant.parse(event.get("occurredAt").asText())));
        return inserted == 1;
    }
}
