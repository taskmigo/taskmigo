package io.taskmigo.eav.schema.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Manages generic versioned EAV schema definitions and immutable published revisions.
public interface EavSchemaService {
    @SuppressWarnings("NullableProblems")
    UUID createDefinition(String key, String name);

    @SuppressWarnings("NullableProblems")
    EavSchemaVersionInfo createDraft(UUID definitionId);

    @SuppressWarnings("NullableProblems")
    EavSchemaVersionInfo replaceDraftFields(UUID definitionId, UUID versionId, List<EavFieldSpec> fields);

    @SuppressWarnings("NullableProblems")
    EavSchemaVersionInfo publish(UUID definitionId, UUID versionId);

    @SuppressWarnings("NullableProblems")
    EavSchemaVersionInfo retire(UUID definitionId, UUID versionId);

    @SuppressWarnings("NullableProblems")
    Optional<EavSchemaVersionInfo> current(UUID definitionId);

    @SuppressWarnings("NullableProblems")
    List<EavSchemaVersionInfo> listVersions(UUID definitionId);

    @SuppressWarnings("NullableProblems")
    EavSchemaVersionInfo version(UUID definitionId, UUID versionId);
}
