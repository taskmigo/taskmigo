package io.taskmigo.audit.model;

import java.util.Arrays;

/// Enumerates entity types currently supported by the audit history API.
public enum AuditEntityType {
    USER("user");

    private final String code;

    AuditEntityType(String code) {
        this.code = code;
    }

    public String code() {
        return this.code;
    }

    /// Resolves an API entity type or raises a transport-neutral invalid-input failure.
    public static AuditEntityType require(String code) {
        return Arrays.stream(values())
            .filter(candidate -> candidate.code.equals(code))
            .findFirst()
            .orElseThrow(() -> AuditException.unsupportedEntityType(code));
    }
}
