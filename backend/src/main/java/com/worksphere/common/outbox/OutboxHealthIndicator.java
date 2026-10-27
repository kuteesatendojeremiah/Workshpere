package com.worksphere.common.outbox;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports DOWN when events have been stuck for more than five minutes, which means "Kafka or the relay
 * is broken". It is part of the overall {@code /actuator/health} but deliberately NOT of readiness: the
 * API works fine without Kafka, so a Kafka problem must not take instances out of rotation. Alerting
 * on this (and on the {@code worksphere_outbox_*} metrics) is how the problem gets noticed instead.
 */
@Component("outbox")
public class OutboxHealthIndicator implements HealthIndicator {

    static final long STUCK_AFTER_SECONDS = 300;

    private final OutboxBacklog backlog;

    public OutboxHealthIndicator(OutboxBacklog backlog) {
        this.backlog = backlog;
    }

    @Override
    public Health health() {
        OutboxBacklog.Snapshot snapshot = backlog.current();
        Health.Builder builder = snapshot.oldestAgeSeconds() > STUCK_AFTER_SECONDS ? Health.down() : Health.up();
        return builder.withDetail("unpublished", snapshot.unpublished())
                .withDetail("oldestAgeSeconds", snapshot.oldestAgeSeconds())
                .build();
    }
}
