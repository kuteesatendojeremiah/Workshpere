package com.worksphere.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.worksphere.common.error.ErrorCode;
import com.worksphere.common.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards the operational endpoints. Health probes ({@code /actuator/health/**}) and
 * {@code /actuator/info} stay public because load balancers and orchestrators cannot log in. Everything
 * else under {@code /actuator/} (the Prometheus metrics, which reveal internals) needs the scrape token
 * as a Bearer token. With no token configured, those endpoints are simply closed.
 *
 * <p>In production, also keep {@code /actuator/**} off the public internet at the reverse proxy
 * (see docs/operations/observability.md); this filter is the second lock, not the only one.
 */
@Component
public class MetricsAccessFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final byte[] token;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public MetricsAccessFilter(@Value("${app.observability.metrics-token:}") String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean isActuator = path.startsWith("/actuator");
        boolean isPublic = path.equals("/actuator/health") || path.startsWith("/actuator/health/")
                || path.equals("/actuator/info");
        return !isActuator || isPublic;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        boolean allowed = token.length > 0
                && header != null
                && header.startsWith(BEARER)
                && MessageDigest.isEqual(token, header.substring(BEARER.length()).getBytes(StandardCharsets.UTF_8));
        if (allowed) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ErrorResponse.of(ErrorCode.UNAUTHENTICATED, "Authentication required", request.getRequestURI()));
    }
}
