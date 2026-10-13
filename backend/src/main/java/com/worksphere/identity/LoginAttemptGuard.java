package com.worksphere.identity;

/**
 * Seam for login rate limiting. Step 20 (Redis) will add a real implementation that tracks
 * failed attempts per email/IP and throws when a caller should be throttled; until then, the
 * no-op implementation lets {@link AuthService} call these hooks without any limiting behavior.
 */
public interface LoginAttemptGuard {

    void beforeAttempt(String email);

    void onFailure(String email);

    void onSuccess(String email);
}
