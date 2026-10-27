package com.worksphere.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Redis cache for the project task summary (spec section 17: cache a project summary, invalidate
 * when tasks change). Three rules keep it safe:
 *
 * <ul>
 *   <li>Authorization happens BEFORE the cache is read (see {@code TaskService.getSummary}), so a
 *       cached value can never be handed to someone who may not see the project.
 *   <li>Entries are evicted AFTER the database transaction commits, so a concurrent reader cannot
 *       re-fill the cache with the old numbers between the eviction and the commit. A 60 second
 *       TTL bounds staleness if an eviction is ever missed.
 *   <li>If Redis is down, reads miss and writes are skipped: slower, never wrong or broken.
 * </ul>
 */
@Component
public class TaskSummaryCache {

    private static final Logger log = LoggerFactory.getLogger(TaskSummaryCache.class);
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    public TaskSummaryCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${app.redis.key-prefix:ws}") String keyPrefix) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
    }

    public Optional<TaskSummaryResponse> get(UUID projectId) {
        try {
            String json = redis.opsForValue().get(key(projectId));
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, TaskSummaryResponse.class));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Task summary cache read failed for project {}: {}", projectId, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(TaskSummaryResponse summary) {
        try {
            redis.opsForValue().set(key(summary.projectId()), objectMapper.writeValueAsString(summary), TTL);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Task summary cache write failed for project {}: {}", summary.projectId(), e.getMessage());
        }
    }

    /** Evicts once the surrounding transaction commits (immediately if there is none). */
    public void evictAfterCommit(UUID projectId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(projectId);
                }
            });
        } else {
            evict(projectId);
        }
    }

    private void evict(UUID projectId) {
        try {
            redis.delete(key(projectId));
        } catch (RuntimeException e) {
            log.warn("Task summary cache eviction failed for project {}: {}", projectId, e.getMessage());
        }
    }

    private String key(UUID projectId) {
        return keyPrefix + ":cache:task-summary:" + projectId;
    }
}
