package io.taskmigo.eav.value.application;

import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Creates and mutates typed EAV instances while keeping every instance pinned to its creation schema version.
public interface EavInstanceService {
    @SuppressWarnings("NullableProblems")
    EavInstanceView create(UUID definitionId, UUID versionId, Map<String, ? extends @Nullable Object> values);

    @SuppressWarnings("NullableProblems")
    EavInstanceView get(UUID instanceId);

    @SuppressWarnings("NullableProblems")
    EavInstanceView patch(UUID instanceId, Map<String, ? extends @Nullable Object> changes);
}
