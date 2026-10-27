package com.worksphere.common.security;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Refuses to start in production with development settings (spec sections 8 and 29: secrets, credential
 * theft). A forgotten default is exactly how a deployed system ends up with a JWT secret that is public in
 * the repository, so the application checks for it and stops with a clear list of what to fix.
 *
 * <p>Active only when {@code app.environment=production}; local development and tests are unaffected.
 * The error lists the NAMES of the problems, never the secret values.
 */
@Component
@ConditionalOnProperty(name = "app.environment", havingValue = "production")
public class ProductionConfigGuard {

    private static final int MIN_SECRET_LENGTH = 32;
    private static final Set<String> KNOWN_WEAK_PASSWORDS = Set.of("worksphere", "worksphere-secret", "change-me", "password");

    private final String jwtSecret;
    private final String webhookSecret;
    private final String metricsToken;
    private final String databasePassword;
    private final String storageSecretKey;
    private final boolean seedDemoData;

    public ProductionConfigGuard(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.billing.webhook-secret}") String webhookSecret,
            @Value("${app.observability.metrics-token:}") String metricsToken,
            @Value("${spring.datasource.password:}") String databasePassword,
            @Value("${app.storage.secret-key:}") String storageSecretKey,
            @Value("${app.seed.demo:false}") boolean seedDemoData) {
        this.jwtSecret = jwtSecret;
        this.webhookSecret = webhookSecret;
        this.metricsToken = metricsToken;
        this.databasePassword = databasePassword;
        this.storageSecretKey = storageSecretKey;
        this.seedDemoData = seedDemoData;
    }

    @PostConstruct
    void failFastOnUnsafeConfiguration() {
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start in production with unsafe configuration:\n - " + String.join("\n - ", problems));
        }
    }

    List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (isWeakSecret(jwtSecret)) {
            problems.add("JWT_SECRET must be a random value of at least " + MIN_SECRET_LENGTH + " characters (not the development default)");
        }
        if (isWeakSecret(webhookSecret)) {
            problems.add("WEBHOOK_SECRET must be a random value of at least " + MIN_SECRET_LENGTH + " characters (not the development default)");
        }
        if (metricsToken == null || metricsToken.length() < 16 || KNOWN_WEAK_PASSWORDS.contains(metricsToken)) {
            problems.add("METRICS_TOKEN must be set to a random value of at least 16 characters");
        }
        if (databasePassword == null || databasePassword.length() < 12 || KNOWN_WEAK_PASSWORDS.contains(databasePassword)) {
            problems.add("DB_PASSWORD must be at least 12 characters and not a well-known default");
        }
        if (storageSecretKey == null || storageSecretKey.length() < 12 || KNOWN_WEAK_PASSWORDS.contains(storageSecretKey)) {
            problems.add("STORAGE_SECRET_KEY must be at least 12 characters and not a well-known default");
        }
        if (seedDemoData) {
            problems.add("SEED_DEMO_DATA must be false: the demo account has a publicly documented password");
        }
        return problems;
    }

    private static boolean isWeakSecret(String secret) {
        return secret == null || secret.length() < MIN_SECRET_LENGTH || secret.contains("local-development-only");
    }
}
