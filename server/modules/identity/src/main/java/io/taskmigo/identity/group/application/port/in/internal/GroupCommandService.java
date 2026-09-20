package io.taskmigo.identity.group.application.port.in.internal;

import io.taskmigo.identity.group.domain.Group;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines Identity-internal Group profile commands executed inside a caller-owned application transaction.
public interface GroupCommandService {
    UUID createRuntime(@Nullable String code, @Nullable String displayName, @Nullable String description);

    GroupMutationResult reconcileManaged(
        @Nullable String code,
        @Nullable String displayName,
        @Nullable String description
    );

    Optional<Group> findByCode(@Nullable String code);

    void delete(Group group);
}
