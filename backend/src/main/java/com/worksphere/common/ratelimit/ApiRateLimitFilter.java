package com.worksphere.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.worksphere.common.error.ErrorCode;
import com.worksphere.common.error.ErrorResponse;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-caller request limits (spec section 29: DoS, expensive API calls, rate limits).
 *
 * <ul>
 *   <li>Signed-in callers are counted by user id, so one noisy user cannot hurt others and a
 *       shared office IP does not share one budget.
 *   <li>Anonymous callers are counted by IP address.
 *   <li>{@code /api/v1/auth/**} (login, register, password reset) gets a much lower limit, since
 *       that is where password guessing and sign-up abuse happen.
 * </ul>
 *
 * Runs after the JWT filter so it knows who the caller is. Over the limit the answer is a
 * standard 429 error body plus {@code Retry-After}.
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final WindowCounter counter;
    private final RateLimitProperties properties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ApiRateLimitFilter(WindowCounter counter, RateLimitProperties properties, MeterRegistry meterRegistry) {
        this.counter = counter;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !properties.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        boolean isAuthEndpoint = request.getRequestURI().startsWith("/api/v1/auth/");
        int limit = isAuthEndpoint ? properties.authPerMinute() : properties.apiPerMinute();
        String key = properties.keyPrefix() + ":rl:" + (isAuthEndpoint ? "auth:" : "api:") + callerId(request);

        if (counter.increment(key, WINDOW) > limit) {
            meterRegistry.counter("worksphere.ratelimit.rejected", "scope", isAuthEndpoint ? "auth" : "api").increment();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getOutputStream(),
                    ErrorResponse.of(
                            ErrorCode.RATE_LIMITED,
                            "Too many requests - slow down and retry shortly",
                            request.getRequestURI()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String callerId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UUID userId) {
            return "user:" + userId;
        }
        // Behind a reverse proxy this would be the proxy's address; the deployment steps configure
        // forwarded headers so getRemoteAddr() is the real client.
        return "ip:" + request.getRemoteAddr();
    }
}
