package io.taskmigo.authorization.role.domain;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Owns canonical Role identity, mutable profile state, and direct Statement assignment.
public final class Role {

    private final UUID id;
    private final RoleCode code;
    private RoleProfile profile;
    private Set<UUID> statementIds;

    private Role(UUID id, RoleCode code, RoleProfile profile, Collection<UUID> statementIds) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.profile = Objects.requireNonNull(profile);
        this.statementIds = Set.copyOf(statementIds);
    }

    /// Creates a new Role with canonical stable identity, profile, and direct Statement assignments.
    public static Role create(
        UUID id,
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> statementIds
    ) {
        return new Role(id, RoleCode.of(code), RoleProfile.of(displayName, description), statementIds);
    }

    /// Reconstitutes canonical persisted Role state.
    public static Role restore(
        UUID id,
        String code,
        String displayName,
        @Nullable String description,
        Collection<UUID> statementIds
    ) {
        return new Role(id, RoleCode.of(code), RoleProfile.of(displayName, description), statementIds);
    }

    public UUID id() {
        return this.id;
    }

    public RoleCode code() {
        return this.code;
    }

    public RoleProfile profile() {
        return this.profile;
    }

    public Set<UUID> statementIds() {
        return this.statementIds;
    }

    /// Reconciles mutable profile and direct Statement assignment while preserving the stable Role code.
    public boolean reconcile(
        @Nullable String displayName,
        @Nullable String description,
        Collection<UUID> statementIds
    ) {
        RoleProfile requestedProfile = RoleProfile.of(displayName, description);
        Set<UUID> requestedStatementIds = Set.copyOf(statementIds);
        boolean changed = !this.profile.equals(requestedProfile) || !this.statementIds.equals(requestedStatementIds);
        if (changed) {
            this.profile = requestedProfile;
            this.statementIds = requestedStatementIds;
        }
        return changed;
    }

    /// Replaces direct Statement assignment using set semantics.
    public boolean replaceStatements(Collection<UUID> statementIds) {
        Set<UUID> requested = Set.copyOf(statementIds);
        if (this.statementIds.equals(requested)) {
            return false;
        }
        this.statementIds = requested;
        return true;
    }
}
