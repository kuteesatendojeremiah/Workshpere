package com.worksphere.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/** Redis being down must never take the API down with it (spec section 17: failure-safe design). */
@ExtendWith(MockitoExtension.class)
class WindowCounterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void whenRedisIsDownIncrementCountsNothingInsteadOfFailing() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(new WindowCounter(redis).increment("k", Duration.ofMinutes(1))).isZero();
    }

    @Test
    void whenRedisIsDownCurrentReadsAsZero() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("k"))).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(new WindowCounter(redis).current("k")).isZero();
    }

    @Test
    void whenRedisIsDownResetDoesNotThrow() {
        when(redis.delete(eq("k"))).thenThrow(new RedisConnectionFailureException("connection refused"));

        new WindowCounter(redis).reset("k"); // no exception is the assertion
    }

    @Test
    void currentReadsTheStoredCount() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("k"))).thenReturn("4");

        assertThat(new WindowCounter(redis).current("k")).isEqualTo(4);
    }
}
