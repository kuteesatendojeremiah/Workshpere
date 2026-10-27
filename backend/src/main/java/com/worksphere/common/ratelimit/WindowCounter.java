package com.worksphere.common.ratelimit;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * A counter in Redis that expires by itself: the building block for rate limiting.
 *
 * <p>Failure-safe by design (spec section 17): if Redis is down or slow, every method logs a
 * warning and behaves as if nothing was counted. Losing rate limiting for a while is better than
 * taking the whole API down with it. The trade-off is documented in ADR-007.
 */
@Component
public class WindowCounter {

    private static final Logger log = LoggerFactory.getLogger(WindowCounter.class);

    // INCR and EXPIRE must happen together. Done as two commands, a crash in between would leave a
    // counter that never expires and blocks someone forever. A Lua script runs atomically.
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    private final StringRedisTemplate redis;

    public WindowCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Adds one and returns the new count. The window starts at the first increment. */
    public long increment(String key, Duration window) {
        try {
            Long count = redis.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(window.toSeconds()));
            return count == null ? 0 : count;
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, not counting {}: {}", key, e.getMessage());
            return 0;
        }
    }

    /** The current count without changing it (0 if unknown or Redis is down). */
    public long current(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, assuming 0 for {}: {}", key, e.getMessage());
            return 0;
        }
    }

    public void reset(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable, could not reset {}: {}", key, e.getMessage());
        }
    }
}
