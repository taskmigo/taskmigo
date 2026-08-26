package io.taskmigo.foundation.pagination;

/// Defines reusable bounds for a cursor-paginated endpoint without imposing feature-specific values.
public record PageLimit(int defaultValue, int maximum) {

    public PageLimit {
        if (defaultValue < 1) throw new IllegalArgumentException("defaultValue must be positive");
        if (maximum < defaultValue) throw new IllegalArgumentException("maximum must be at least defaultValue");
    }

    /// Validates an explicit page size against this policy.
    public int require(int requested) {
        if (requested < 1 || requested > this.maximum) {
            throw new IllegalArgumentException("limit must be between 1 and " + this.maximum);
        }
        return requested;
    }
}
