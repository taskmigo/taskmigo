package io.taskmigo.language;

import java.util.Objects;

/// Describes the stable identity and statically checked meaning of one resource field.
public record Field(FieldId id, FieldPath path, LanguageType type, boolean nullable) {
    public Field {
        Objects.requireNonNull(id);
        Objects.requireNonNull(path);
        Objects.requireNonNull(type);
    }
}
