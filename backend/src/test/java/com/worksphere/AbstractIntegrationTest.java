package com.worksphere;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real PostgreSQL database. The container's connection
 * details are wired into Spring's datasource automatically via {@link ServiceConnection},
 * and Flyway migrations run against it on context startup exactly as they would in production.
 *
 * <p>Started once (singleton pattern) and never stopped by us - the JVM-exit Ryuk reaper cleans
 * it up. We deliberately do NOT use {@code @Testcontainers}/{@code @Container}: that combination
 * stops and restarts this container between test classes (new random port each time), which
 * breaks Spring's cached application context when it's reused across classes - the cached
 * context keeps a connection pool pointed at the now-dead old port.
 */
@SpringBootTest(
        properties = {
            // Generous defaults so the ~190 other tests (which all share one IP) are never throttled;
            // RateLimitIntegrationTest overrides these with tiny values and its own key prefix.
            "app.rate-limit.enabled=true",
            // The tests read invitation / reset tokens from the log; real deployments leave this off.
            "app.mail.log-tokens=true",
            "app.rate-limit.api-per-minute=100000",
            "app.rate-limit.auth-per-minute=100000"
        })
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    /** A fake SMTP server with an HTTP API to read what was sent (port 1025 = SMTP, 8025 = HTTP). */
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.20")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

    /**
     * S3-compatible object storage. MinIO's own images are no longer published to Docker Hub, so this
     * uses the last Bitnami build: fine for development and tests, NOT for production (use a managed
     * S3 service there; only app.storage.* settings change).
     */
    static final GenericContainer<?> MINIO = new GenericContainer<>("bitnamilegacy/minio:latest")
            .withEnv("MINIO_ROOT_USER", "worksphere-test")
            .withEnv("MINIO_ROOT_PASSWORD", "worksphere-test-secret")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    @DynamicPropertySource
    static void externalServices(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        registry.add("app.storage.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("app.storage.access-key", () -> "worksphere-test");
        registry.add("app.storage.secret-key", () -> "worksphere-test-secret");
    }

    /** Base URL of the Mailpit HTTP API, for tests that check which emails were sent. */
    protected static String mailpitUrl() {
        return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
    }

    static {
        MAILPIT.start();
        MINIO.start();
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
    }
}
