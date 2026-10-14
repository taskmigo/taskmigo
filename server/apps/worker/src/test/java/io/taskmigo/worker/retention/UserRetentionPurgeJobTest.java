package io.taskmigo.worker.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.taskmigo.identity.user.application.api.UserRetentionService;
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
     * Verifies that the retention Worker job delegates purge work using the current clock instant.
     *
     * Given: a fixed clock at 2026-10-03T00:00:00Z and a mocked User retention application API.
     * Expect: the job invokes purgeExpiredUsers exactly once with that instant.
     */
    @Test
    @DisplayName("uses the current instant for user retention tombstone processing")
    void shouldUseCurrentInstantWhenRetentionJobExecutes() {
        // Arrange
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        UserRetentionService retention = mock(UserRetentionService.class);
        UserRetentionPurgeJob job = new UserRetentionPurgeJob(retention, Clock.fixed(now, ZoneOffset.UTC));

        // Act
        job.purgeExpiredUsers();

        // Assert
        verify(retention).purgeExpiredUsers(now);
    }

    /**
     * Verifies that the retention Worker job remains scheduled at a one-hour fixed delay.
     *
     * Given: the scheduled purgeExpiredUsers method on UserRetentionPurgeJob.
     * Expect: its schedule uses fixedDelay = 1 with TimeUnit.HOURS.
     */
    @Test
    @DisplayName("runs user retention tombstone processing hourly")
    void shouldRunHourlyWhenRetentionScheduleIsInspected() throws Exception {
        // Arrange
        Method method = UserRetentionPurgeJob.class.getDeclaredMethod("purgeExpiredUsers");

        // Act
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        // Assert
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelay()).isEqualTo(1);
        assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.HOURS);
    }
}
