package com.worksphere.common.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/** Publishes the outbox backlog as Prometheus gauges. Alert on these (see infra/observability). */
@Component
public class OutboxMetrics implements MeterBinder {

    private final OutboxBacklog backlog;

    public OutboxMetrics(OutboxBacklog backlog) {
        this.backlog = backlog;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("worksphere.outbox.unpublished", () -> safe(() -> backlog.current().unpublished()))
                .description("Domain events saved but not yet published to Kafka")
                .register(registry);
        Gauge.builder("worksphere.outbox.oldest.age.seconds", () -> safe(() -> backlog.current().oldestAgeSeconds()))
                .description("Age of the oldest unpublished domain event")
                .baseUnit("seconds")
                .register(registry);
    }

    /** A gauge must never throw; if the database is unreachable the scrape reports NaN instead of failing. */
    private static double safe(java.util.function.LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
