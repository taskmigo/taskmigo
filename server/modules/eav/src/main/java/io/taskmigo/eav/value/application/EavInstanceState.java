package io.taskmigo.eav.value.application;

import io.taskmigo.eav.schema.domain.EavValue;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// Carries one persisted EAV instance at the application persistence boundary.
public record EavInstanceState(UUID id, UUID schemaDefinitionId, UUID schemaVersionId, Map<UUID, EavValue> values) {
    public EavInstanceState {
        Objects.requireNonNull(id);
        Objects.requireNonNull(schemaDefinitionId);
        Objects.requireNonNull(schemaVersionId);
        values = Map.copyOf(Objects.requireNonNull(values));
    }
}
