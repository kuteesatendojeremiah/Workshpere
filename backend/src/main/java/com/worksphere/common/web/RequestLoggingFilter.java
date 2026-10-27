package com.worksphere.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One log line per request: method, path, status and how long it took. Together with the
 * {@code requestId} that every log line carries, this is what lets someone follow a single failing
 * request through the logs ("diagnose from logs and metrics", spec section 25).
 *
 * <p>Only the PATH is logged, never the query string or headers, because those can carry tokens or
 * personal data. Health probes and metric scrapes are skipped: they would drown out real traffic.
 * Server errors are logged as WARN so they stand out.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http.access");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || path.equals("/health") || path.startsWith("/ws");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();
            MDC.put("http.status", String.valueOf(status));
            MDC.put("http.durationMs", String.valueOf(millis));
            try {
                if (status >= 500) {
                    log.warn("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), status, millis);
                } else {
                    log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), status, millis);
                }
            } finally {
                MDC.remove("http.status");
                MDC.remove("http.durationMs");
            }
        }
    }
}
