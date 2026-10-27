package com.worksphere.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MetricsAccessFilterTest {

    private MockHttpServletResponse call(MetricsAccessFilter filter, String path, String authorization)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // Reaching the end of the chain means the filter let the request through.
        response.setCommitted(false);
        return chain.getRequest() != null && response.getStatus() == 200 && response.getContentAsString().isEmpty()
                ? response
                : response;
    }

    private boolean allowed(MetricsAccessFilter filter, String path, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain.getRequest() != null;
    }

    @Test
    void healthProbesAndInfoAreAlwaysOpen() throws Exception {
        MetricsAccessFilter filter = new MetricsAccessFilter("secret");

        assertThat(allowed(filter, "/actuator/health", null)).isTrue();
        assertThat(allowed(filter, "/actuator/health/liveness", null)).isTrue();
        assertThat(allowed(filter, "/actuator/health/readiness", null)).isTrue();
        assertThat(allowed(filter, "/actuator/info", null)).isTrue();
    }

    @Test
    void metricsNeedTheExactBearerToken() throws Exception {
        MetricsAccessFilter filter = new MetricsAccessFilter("secret");

        assertThat(allowed(filter, "/actuator/prometheus", null)).isFalse();
        assertThat(allowed(filter, "/actuator/prometheus", "Bearer wrong")).isFalse();
        assertThat(allowed(filter, "/actuator/prometheus", "Bearer secre")).isFalse();
        assertThat(allowed(filter, "/actuator/prometheus", "Bearer secret ")).isFalse();
        assertThat(allowed(filter, "/actuator/prometheus", "Basic secret")).isFalse();
        assertThat(allowed(filter, "/actuator/prometheus", "Bearer secret")).isTrue();
    }

    @Test
    void anyOtherActuatorPathIsClosedWithoutTheToken() throws Exception {
        MetricsAccessFilter filter = new MetricsAccessFilter("secret");

        assertThat(allowed(filter, "/actuator/env", null)).isFalse();
        assertThat(allowed(filter, "/actuator/heapdump", null)).isFalse();
        assertThat(allowed(filter, "/actuator/healthy-looking", null)).isFalse();
    }

    @Test
    void withNoTokenConfiguredTheEndpointIsClosedEvenForAnEmptyToken() throws Exception {
        MetricsAccessFilter filter = new MetricsAccessFilter("");

        assertThat(allowed(filter, "/actuator/prometheus", null)).isFalse();
        assertThat(allowed(filter, "/actuator/prometheus", "Bearer ")).isFalse();
        assertThat(allowed(filter, "/actuator/prometheus", "Bearer")).isFalse();
    }

    @Test
    void theApiItselfIsNotAffected() throws Exception {
        assertThat(allowed(new MetricsAccessFilter("secret"), "/api/v1/plans", null)).isTrue();
    }

    @Test
    void aRefusedRequestGetsTheStandardErrorShapeAndNoMetrics() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setRequestURI("/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new MetricsAccessFilter("secret").doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"").doesNotContain("jvm_");
    }
}
