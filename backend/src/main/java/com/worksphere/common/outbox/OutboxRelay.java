package com.worksphere.common.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes outbox rows to Kafka (spec 16.3). Delivery is AT LEAST ONCE: if the process dies after
 * Kafka accepted a message but before the row is marked published, the event is sent again after
 * restart. That is why every consumer must be idempotent (see {@code ActivityFeedConsumer}).
 *
 * <p>Ordering: events are sent oldest first, keyed by aggregate id (so everything about one task
 * lands on one partition, in order), and the batch stops at the first failure rather than skipping
 * ahead. If Kafka is down the rows simply wait; the API keeps working, which is the point.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;
    private static final Duration KEEP_PUBLISHED_FOR = Duration.ofDays(7);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final MeterRegistry meterRegistry;

    public OutboxRelay(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.outbox.topic:worksphere.events}") String topic,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    @Transactional
    public void relayPending() {
        List<OutboxEvent> batch = repository.lockNextBatch(BATCH_SIZE);
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate
                        .send(topic, event.getAggregateId().toString(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.markPublished();
                meterRegistry.counter("worksphere.outbox.published").increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                event.markFailed(e.getMessage());
                meterRegistry.counter("worksphere.outbox.publish.failures").increment();
                log.warn("Could not publish outbox event {} (attempt {}): {}", event.getId(), event.getAttempts(),
                        e.getMessage());
                return; // keep order: do not publish later events before this one
            }
        }
    }

    /** Published rows are only kept for a week, for debugging; the table must not grow forever. */
    @Scheduled(cron = "${app.outbox.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void deleteOldPublishedEvents() {
        int deleted = repository.deletePublishedBefore(Instant.now().minus(KEEP_PUBLISHED_FOR));
        if (deleted > 0) {
            log.info("Deleted {} published outbox events older than {}", deleted, KEEP_PUBLISHED_FOR);
        }
    }
}
