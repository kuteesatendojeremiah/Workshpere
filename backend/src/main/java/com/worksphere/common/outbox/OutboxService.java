package com.worksphere.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place other modules call to announce a domain event, like AuditService and
 * NotificationService.
 *
 * <p>{@code Propagation.MANDATORY} is the whole point of the outbox pattern: this method refuses to
 * run outside a transaction, so the event row is always saved in the SAME transaction as the
 * business change. If that transaction rolls back, the event disappears with it; if it commits, the
 * event is guaranteed to exist and will be delivered.
 *
 * <p>Events carry ids and a few safe facts, never passwords, tokens or personal data, because they
 * leave the database and may be read by many consumers.
 */
@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            DomainEventType type, String aggregateType, UUID aggregateId, UUID organizationId, Map<String, Object> data) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", type.name());
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("organizationId", organizationId);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("data", data == null ? Map.of() : data);

        try {
            outboxEventRepository.save(new OutboxEvent(
                    eventId, type.name(), aggregateType, aggregateId, organizationId,
                    objectMapper.writeValueAsString(envelope)));
        } catch (JsonProcessingException e) {
            // Failing loudly rolls the business change back too, which is what we want:
            // better no change than a change nobody is told about.
            throw new IllegalStateException("Could not serialize domain event " + type, e);
        }
    }
}
