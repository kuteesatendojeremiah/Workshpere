package com.worksphere.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.mail.*}: who emails come from, where links point, and the dev-only token log switch. */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String from, String frontendUrl, boolean logTokens) {

    public MailProperties {
        if (from == null || from.isBlank()) {
            from = "no-reply@worksphere.local";
        }
        if (frontendUrl == null || frontendUrl.isBlank()) {
            frontendUrl = "http://localhost:5173";
        }
    }
}
