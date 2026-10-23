package io.taskmigo.eav.schema.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Persists generic EAV schema definitions and version projections for the application service.
public interface EavSchemaRepository {
    @SuppressWarnings("NullableProblems")
    void createDefinition(UUID id, String key, String name);

    @SuppressWarnings("NullableProblems")
    boolean definitionExists(UUID definitionId);

    /// Acquires the repository's write lock for one schema definition.
    @SuppressWarnings("NullableProblems")
    void lockDefinition(UUID definitionId);

    @SuppressWarnings("NullableProblems")
    long nextVersion(UUID definitionId);

    @SuppressWarnings("NullableProblems")
    void insertVersion(EavSchemaVersionInfo version);

    @SuppressWarnings("NullableProblems")
    void updateVersion(EavSchemaVersionInfo version);

    @SuppressWarnings("NullableProblems")
    Optional<EavSchemaVersionInfo> findVersion(UUID definitionId, UUID versionId);

    @SuppressWarnings("NullableProblems")
    List<EavSchemaVersionInfo> findVersions(UUID definitionId);

    @SuppressWarnings("NullableProblems")
    Optional<EavSchemaVersionInfo> current(UUID definitionId);

    @SuppressWarnings("NullableProblems")
    Optional<EavFieldSpec> publishedField(UUID definitionId, UUID fieldId);

    @SuppressWarnings("NullableProblems")
    void setCurrentVersion(UUID definitionId, @Nullable UUID versionId);
}
