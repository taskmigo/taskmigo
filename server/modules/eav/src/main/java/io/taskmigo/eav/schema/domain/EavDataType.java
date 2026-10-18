package io.taskmigo.eav.schema.domain;

import java.util.Set;

/// Enumerates the finite value types supported by versioned EAV schemas.
public enum EavDataType {
    STRING(Set.of("minLength", "maxLength")),
    NUMBER(Set.of("minimum", "maximum")),
    BOOLEAN(Set.of()),
    DATE(Set.of("minimum", "maximum")),
    DATETIME(Set.of("minimum", "maximum")),
    UUID(Set.of()),
    ENUM(Set.of("options"));

    private final Set<String> constraintNames;

    EavDataType(Set<String> constraintNames) {
        this.constraintNames = Set.copyOf(constraintNames);
    }

    boolean supportsConstraint(String name) {
        return this.constraintNames.contains(name);
    }
}
