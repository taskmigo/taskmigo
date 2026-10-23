package io.taskmigo.eav.schema.application;

import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavFieldDefinition;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// Describes one logical custom field at the EAV schema application boundary.
public record EavFieldSpec(
    UUID fieldId,
    String key,
    String label,
    EavDataType dataType,
    boolean required,
    Map<String, Object> constraints
) {
    public EavFieldSpec {
        Objects.requireNonNull(fieldId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(label);
        Objects.requireNonNull(dataType);
        constraints = Map.copyOf(Objects.requireNonNull(constraints));
        EavFieldDefinition definition = new EavFieldDefinition(fieldId, key, label, dataType, required, constraints);
        fieldId = definition.fieldId();
        key = definition.key();
        label = definition.label();
        dataType = definition.dataType();
        required = definition.required();
        constraints = definition.constraints();
    }

    EavFieldDefinition toDefinition() {
        return new EavFieldDefinition(
            this.fieldId,
            this.key,
            this.label,
            this.dataType,
            this.required,
            this.constraints
        );
    }
}
