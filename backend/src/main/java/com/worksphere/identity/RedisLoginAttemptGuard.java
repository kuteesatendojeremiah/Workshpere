package com.worksphere.identity;

import com.worksphere.common.error.RateLimitedException;
import com.worksphere.common.ratelimit.RateLimitProperties;
import com.worksphere.common.ratelimit.WindowCounter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Locks an email out of logging in after too many wrong passwords in a row (spec 24.1, brute
 * force). The counter lives in Redis with a TTL, so the lockout lifts by itself and works across
 * several backend instances.
 *
 * <p>The email is hashed in the key so Redis never holds a readable list of addresses. Counting
 * by email (not IP) protects one account from a distributed guessing attack; the IP-based limit
 * on /api/v1/auth/** in {@code ApiRateLimitFilter} covers one machine trying many accounts.
 */
@Component
public class RedisLoginAttemptGuard implements LoginAttemptGuard {

    private final WindowCounter counter;
    private final RateLimitProperties properties;

    public RedisLoginAttemptGuard(WindowCounter counter, RateLimitProperties properties) {
        this.counter = counter;
        this.properties = properties;
    }

    @Override
    public void beforeAttempt(String email) {
        if (properties.enabled() && counter.current(key(email)) >= properties.loginFailuresBeforeLockout()) {
            throw new RateLimitedException("Too many failed sign-in attempts - try again later");
        }
    }

    @Override
    public void onFailure(String email) {
        if (properties.enabled()) {
            counter.increment(key(email), Duration.ofMinutes(properties.loginLockoutMinutes()));
        }
    }

    @Override
    public void onSuccess(String email) {
        if (properties.enabled()) {
            counter.reset(key(email));
        }
    }

    private String key(String email) {
        return properties.keyPrefix() + ":login-fail:" + sha256(email.trim().toLowerCase(Locale.ROOT));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
