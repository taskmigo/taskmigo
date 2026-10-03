package io.taskmigo.identity.user.application.port.in.internal;

import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines Identity-internal User command use cases executed inside a caller-owned application transaction.
public interface UserCommandService {
    UUID createRuntime(
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    );

    UserMutationResult reconcileManaged(
        @Nullable String username,
        @Nullable String initialPasswordHash,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    );

    boolean lock(UUID id);

    Optional<User> findByIdForUpdate(UUID id);

    Optional<User> findByUsername(@Nullable String username);

    Optional<User> findByUsernameForUpdate(@Nullable String username);

    List<RetainedUserCandidate> retainedCandidates(Instant cutoff, @Nullable RetainedUserCandidate after);

    Optional<User> claimRetainedForUpdate(UUID id);

    void save(User user);
}
