package io.taskmigo.identity.group.domain;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Owns canonical Group identity and mutable profile invariants.
public final class Group {

    private final UUID id;
    private final GroupCode code;
    private GroupProfile profile;

    private Group(UUID id, GroupCode code, GroupProfile profile) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.profile = Objects.requireNonNull(profile);
    }

    /// Creates a new Group with normalized stable identity and profile state.
    public static Group create(
        UUID id,
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description
    ) {
        return new Group(id, GroupCode.of(code), GroupProfile.of(displayName, description));
    }

    /// Reconstitutes persisted canonical Group state.
    public static Group restore(UUID id, String code, String displayName, @Nullable String description) {
        return new Group(id, GroupCode.of(code), GroupProfile.of(displayName, description));
    }

    public UUID id() {
        return this.id;
    }

    public GroupCode code() {
        return this.code;
    }

    public GroupProfile profile() {
        return this.profile;
    }

    /// Reconciles mutable profile state while preserving the stable Group code.
    public boolean reconcileProfile(@Nullable String displayName, @Nullable String description) {
        GroupProfile requested = GroupProfile.of(displayName, description);
        if (this.profile.equals(requested)) {
            return false;
        }
        this.profile = requested;
        return true;
    }
}
