package io.taskmigo.eav.value.application;

import java.util.Optional;
import java.util.UUID;

/// Persists EAV schema instances together with the values pinned to their historical schema version.
public interface EavInstanceRepository {
    @SuppressWarnings("NullableProblems")
    void create(EavInstanceState state);

    @SuppressWarnings("NullableProblems")
    Optional<EavInstanceState> find(UUID instanceId);

    @SuppressWarnings("NullableProblems")
    void update(EavInstanceState state);
}
