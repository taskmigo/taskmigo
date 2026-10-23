package io.taskmigo.eav.value.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// Exposes one EAV instance using schema field keys while preserving its pinned schema identity.
public record EavInstanceView(UUID id, UUID schemaDefinitionId, UUID schemaVersionId, Map<String, Object> values) {
    public EavInstanceView {
        Objects.requireNonNull(id);
        Objects.requireNonNull(schemaDefinitionId);
        Objects.requireNonNull(schemaVersionId);
        values = Map.copyOf(Objects.requireNonNull(values));
    }
}
