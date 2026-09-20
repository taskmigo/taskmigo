package io.taskmigo.identity.group.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Represents normalized mutable Group profile state.
public final class GroupProfile {

    private final String displayName;
    private final @Nullable String description;

    private GroupProfile(String displayName, @Nullable String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /// Creates profile state using the canonical display-name normalization rule.
    public static GroupProfile of(@Nullable String displayName, @Nullable String description) {
        return new GroupProfile(required(displayName, "displayName"), description);
    }

    public String displayName() {
        return this.displayName;
    }

    public @Nullable String description() {
        return this.description;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GroupProfile profile)) {
            return false;
        }
        return this.displayName.equals(profile.displayName) && Objects.equals(this.description, profile.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.displayName, this.description);
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw GroupRuleViolation.required(field);
        }
        return value.trim();
    }
}
