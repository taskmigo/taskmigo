package io.taskmigo.identity.configuration;

import java.time.Duration;
import java.time.format.DateTimeParseException;

/// Represents a User retention period restricted to non-negative whole 24-hour days.
public record RetentionDuration(Duration duration) {
    private static final long SECONDS_PER_DAY = Duration.ofDays(1).getSeconds();

    public RetentionDuration {
        if (duration.isNegative() || duration.getNano() != 0 || duration.getSeconds() % SECONDS_PER_DAY != 0) {
            throw new IllegalArgumentException("Retention duration must be a non-negative whole number of days");
        }
    }

    /// Parses an ISO-8601 duration and validates Taskmigo's whole-day retention precision.
    public static RetentionDuration parse(String value) {
        try {
            return new RetentionDuration(Duration.parse(value));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Retention duration must be a valid ISO-8601 duration", exception);
        }
    }

    /// Returns the number of retained 24-hour days.
    public long days() {
        return this.duration.toDays();
    }

    /// Returns whether deletion must tombstone the User immediately.
    public boolean immediate() {
        return this.duration.isZero();
    }

    @Override
    public String toString() {
        return "P" + this.days() + "D";
    }
}
