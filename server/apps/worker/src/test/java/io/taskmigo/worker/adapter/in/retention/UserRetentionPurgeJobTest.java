package io.taskmigo.worker.adapter.in.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.taskmigo.identity.user.application.port.in.api.UserRetentionService;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class UserRetentionPurgeJobTest {

    /**
     * Given: the retention Worker job and a fixed clock.
     * Expect: one invocation uses the current clock instant and the schedule is exactly hourly.
     */
    @Test
    @DisplayName("runs user retention tombstone processing hourly")
    void shouldRunRetentionTombstoneProcessingHourly() throws Exception {
        // Arrange
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        UserRetentionService retention = mock(UserRetentionService.class);
        UserRetentionPurgeJob job = new UserRetentionPurgeJob(retention, Clock.fixed(now, ZoneOffset.UTC));

        // Act
        job.purgeExpiredUsers();

        // Assert
        verify(retention).purgeExpiredUsers(now);

        Method method = UserRetentionPurgeJob.class.getDeclaredMethod("purgeExpiredUsers");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelay()).isEqualTo(1);
        assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.HOURS);
    }
}
