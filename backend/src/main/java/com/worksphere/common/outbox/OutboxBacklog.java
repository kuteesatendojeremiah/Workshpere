package com.worksphere.common.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * How far behind the outbox relay is: how many events wait to be published and how old the oldest
 * one is. This is THE number to watch for event delivery: if it keeps growing, Kafka (or the relay)
 * is unhealthy even though the API still answers. Read by both the metrics and the health indicator;
 * cached for a few seconds so a busy scraper cannot turn into a busy database.
 */
@Component
public class OutboxBacklog {

    public record Snapshot(long unpublished, long oldestAgeSeconds) {
    }

    private static final Duration CACHE_FOR = Duration.ofSeconds(5);

    private final JdbcTemplate jdbc;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    private record Cached(Instant at, Snapshot snapshot) {
    }

    public OutboxBacklog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Snapshot current() {
        Cached cached = cache.get();
        Instant now = Instant.now();
        if (cached != null && Duration.between(cached.at(), now).compareTo(CACHE_FOR) < 0) {
            return cached.snapshot();
        }
        Snapshot fresh = jdbc.queryForObject(
                "SELECT count(*), COALESCE(EXTRACT(EPOCH FROM (now() - min(created_at)))::bigint, 0) "
                        + "FROM outbox_events WHERE published_at IS NULL",
                (rs, rowNum) -> new Snapshot(rs.getLong(1), rs.getLong(2)));
        cache.set(new Cached(now, fresh));
        return fresh;
    }
}
