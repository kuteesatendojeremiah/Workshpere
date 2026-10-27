package com.worksphere.common.ratelimit;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * {@code worksphere_redis_up}: 1 if Redis answers PING, otherwise 0.
 *
 * <p>Redis is intentionally NOT part of readiness (the API fails open without it, see ADR-007), so a
 * silent Redis outage would otherwise go unnoticed while rate limiting and caching quietly stop
 * working. This gauge, with an alert on it, is what makes that outage visible. The answer is cached for
 * a few seconds so frequent scrapes cost nothing.
 */
@Component
public class RedisUpMetric implements MeterBinder {

    private static final Duration CACHE_FOR = Duration.ofSeconds(5);

    private record Checked(Instant at, double up) {
    }

    private final RedisConnectionFactory connectionFactory;
    private final AtomicReference<Checked> last = new AtomicReference<>();

    public RedisUpMetric(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("worksphere.redis.up", this::check)
                .description("1 if Redis answers PING, 0 if it is unreachable (rate limiting and caching are then off)")
                .register(registry);
    }

    private double check() {
        Checked cached = last.get();
        Instant now = Instant.now();
        if (cached != null && Duration.between(cached.at(), now).compareTo(CACHE_FOR) < 0) {
            return cached.up();
        }
        double up;
        try (var connection = connectionFactory.getConnection()) {
            up = "PONG".equalsIgnoreCase(connection.ping()) ? 1 : 0;
        } catch (RuntimeException e) {
            up = 0;
        }
        last.set(new Checked(now, up));
        return up;
    }
}
