package io.taskmigo.eav.schema.application;

import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/// Provides an immutable projection of one EAV schema revision.
public record EavSchemaVersionInfo(
    UUID id,
    UUID definitionId,
    long version,
    EavSchemaStatus status,
    List<EavFieldSpec> fields,
    Optional<Instant> publishedAt,
    Optional<Instant> retiredAt
) {
    public EavSchemaVersionInfo {
        Objects.requireNonNull(id);
        Objects.requireNonNull(definitionId);
        Objects.requireNonNull(status);
        if (version < 1) {
            throw new IllegalArgumentException("EAV schema version must be positive");
        }
        fields = List.copyOf(Objects.requireNonNull(fields));
        Objects.requireNonNull(publishedAt);
        Objects.requireNonNull(retiredAt);
    }

    static EavSchemaVersionInfo draft(UUID id, UUID definitionId, long version, List<EavFieldSpec> fields) {
        return new EavSchemaVersionInfo(
            id,
            definitionId,
            version,
            EavSchemaStatus.DRAFT,
            fields,
            Optional.empty(),
            Optional.empty()
        );
    }
}
