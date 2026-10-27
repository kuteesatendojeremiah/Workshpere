package com.worksphere.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionConfigGuardTest {

    private static final String STRONG = "k3Jx9Qm2Wz7Lp5Vn8Tc1Rb6Yd4Fh0Gs3Aa";

    private ProductionConfigGuard guard(String jwt, String webhook, String metrics, String db, String storage, boolean seed) {
        return new ProductionConfigGuard(jwt, webhook, metrics, db, storage, seed);
    }

    private ProductionConfigGuard strongConfiguration() {
        return guard(STRONG, STRONG + "x", "metrics-token-1234567", "db-password-123456", "storage-secret-1234", false);
    }

    @Test
    void aProperlyConfiguredProductionStartsNormally() {
        assertThat(strongConfiguration().problems()).isEmpty();
        strongConfiguration().failFastOnUnsafeConfiguration(); // no exception
    }

    @Test
    void theDevelopmentDefaultsAreRefused() {
        ProductionConfigGuard guard = guard(
                "local-development-only-secret-key-never-use-this-in-prod",
                "local-development-only-webhook-secret",
                "",
                "worksphere",
                "worksphere-secret",
                true);

        assertThat(guard.problems()).hasSize(6);
        assertThatThrownBy(guard::failFastOnUnsafeConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("WEBHOOK_SECRET")
                .hasMessageContaining("METRICS_TOKEN")
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("STORAGE_SECRET_KEY")
                .hasMessageContaining("SEED_DEMO_DATA");
    }

    @Test
    void theErrorNamesTheProblemsButNeverPrintsTheSecrets() {
        ProductionConfigGuard guard = guard("short-but-secret", STRONG, "metrics-token-1234567", "db-password-123456",
                "storage-secret-1234", false);

        assertThatThrownBy(guard::failFastOnUnsafeConfiguration)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageNotContaining("short-but-secret");
    }

    @Test
    void aShortJwtSecretIsRefusedEvenIfItIsNotTheDefault() {
        assertThat(guard("only-20-characters!!", STRONG, "metrics-token-1234567", "db-password-123456",
                        "storage-secret-1234", false).problems())
                .singleElement()
                .asString()
                .contains("JWT_SECRET");
    }

    @Test
    void demoDataIsNeverAllowedInProduction() {
        assertThat(guard(STRONG, STRONG, "metrics-token-1234567", "db-password-123456", "storage-secret-1234", true)
                        .problems())
                .singleElement()
                .asString()
                .contains("SEED_DEMO_DATA");
    }
}
