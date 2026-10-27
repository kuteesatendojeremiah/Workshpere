package com.worksphere.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class OutboxHealthIndicatorTest {

    private OutboxHealthIndicator indicatorWith(long unpublished, long oldestAgeSeconds) {
        OutboxBacklog backlog = mock(OutboxBacklog.class);
        when(backlog.current()).thenReturn(new OutboxBacklog.Snapshot(unpublished, oldestAgeSeconds));
        return new OutboxHealthIndicator(backlog);
    }

    @Test
    void aShortOrEmptyBacklogIsHealthy() {
        assertThat(indicatorWith(0, 0).health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicatorWith(40, 30).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void eventsStuckForMoreThanFiveMinutesMeanKafkaOrTheRelayIsBroken() {
        var health = indicatorWith(12, 301).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("unpublished", 12L).containsEntry("oldestAgeSeconds", 301L);
    }

    @Test
    void exactlyFiveMinutesIsStillTolerated() {
        assertThat(indicatorWith(1, 300).health().getStatus()).isEqualTo(Status.UP);
    }
}
