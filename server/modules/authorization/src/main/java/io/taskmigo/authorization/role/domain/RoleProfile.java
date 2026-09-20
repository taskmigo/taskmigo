package io.taskmigo.authorization.role.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Represents normalized mutable Role profile state.
public final class RoleProfile {

    private static final int MAX_DISPLAY_NAME_LENGTH = 255;

    private final String displayName;
    private final @Nullable String description;

    private RoleProfile(String displayName, @Nullable String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /// Creates canonical Role profile state with a required, trimmed display name.
    public static RoleProfile of(@Nullable String displayName, @Nullable String description) {
        if (displayName == null || displayName.isBlank()) {
            throw RoleRuleViolation.required("displayName");
        }
        String normalized = displayName.trim();
        if (normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw RoleRuleViolation.tooLong("displayName", MAX_DISPLAY_NAME_LENGTH);
        }
        return new RoleProfile(normalized, description);
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
        if (!(other instanceof RoleProfile profile)) {
            return false;
        }
        return this.displayName.equals(profile.displayName) && Objects.equals(this.description, profile.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.displayName, this.description);
    }
}
