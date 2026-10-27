package com.worksphere.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits per one-minute window. {@code keyPrefix} namespaces the Redis keys so several
 * environments (or test contexts) can share one Redis without sharing counters.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        String keyPrefix,
        int apiPerMinute,
        int authPerMinute,
        int loginFailuresBeforeLockout,
        int loginLockoutMinutes) {

    public RateLimitProperties {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "ws";
        }
        if (apiPerMinute <= 0) {
            apiPerMinute = 300;
        }
        if (authPerMinute <= 0) {
            authPerMinute = 30;
        }
        if (loginFailuresBeforeLockout <= 0) {
            loginFailuresBeforeLockout = 5;
        }
        if (loginLockoutMinutes <= 0) {
            loginLockoutMinutes = 15;
        }
    }
}
