package io.taskmigo.identity.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetentionDurationTest {

    /**
     * Verifies that equivalent ISO-8601 whole-day durations normalize to the same persisted representation.
     *
     * Given: P1D and PT24H.
     * Expect: both values resolve to one day and render as P1D.
     */
    @Test
    @DisplayName("normalizes equivalent whole-day retention durations")
    void shouldNormalizeRetentionDurationWhenInputIsWholeDays() {
        // Arrange + Act
        RetentionDuration days = RetentionDuration.parse("P1D");
        RetentionDuration hours = RetentionDuration.parse("PT24H");

        // Assert
        assertThat(days.duration()).isEqualTo(Duration.ofDays(1));
        assertThat(hours).isEqualTo(days);
        assertThat(hours.toString()).isEqualTo("P1D");
    }

    /**
     * Verifies that retention precision is restricted to non-negative whole 24-hour days.
     *
     * Given: one-hour, thirty-six-hour, and negative one-day durations.
     * Expect: each value is rejected before it can be persisted.
     */
    @Test
    @DisplayName("rejects retention durations that are not non-negative whole days")
    void shouldRejectRetentionDurationWhenInputIsNotNonNegativeWholeDays() {
        // Act + Assert
        assertThatThrownBy(() -> RetentionDuration.parse("PT1H")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetentionDuration.parse("PT36H")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RetentionDuration.parse("-P1D")).isInstanceOf(IllegalArgumentException.class);
    }
}
