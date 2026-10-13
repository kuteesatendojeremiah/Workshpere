package com.worksphere;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

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
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
